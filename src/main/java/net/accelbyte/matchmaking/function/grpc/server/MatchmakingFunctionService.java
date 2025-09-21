package net.accelbyte.matchmaking.function.grpc.server;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.ListValue;
import com.google.protobuf.util.JsonFormat;
import lombok.extern.slf4j.Slf4j;

import com.google.gson.Gson;
import net.accelbyte.matchmakingv2.matchfunction.*;
import net.accelbyte.matchmaking.util.ConversionUtils;
import io.grpc.stub.StreamObserver;
import org.lognet.springboot.grpc.GRpcService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@GRpcService
public class MatchmakingFunctionService extends MatchFunctionGrpc.MatchFunctionImplBase {
    private int shipCountMin = 2;
    private int shipCountMax = 2;
    private GameRules currentRules = null;

    @Override
    public void getStatCodes(GetStatCodesRequest request, StreamObserver<StatCodesResponse> responseObserver) {
        log.info("Received get stat codes request");
        StatCodesResponse response = StatCodesResponse.newBuilder().build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void validateTicket(ValidateTicketRequest request, StreamObserver<ValidateTicketResponse> responseObserver) {
        log.info("Received validate ticket request");
        ValidateTicketResponse response = ValidateTicketResponse.newBuilder().setValidTicket(true).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void enrichTicket(EnrichTicketRequest request, StreamObserver<EnrichTicketResponse> responseObserver) {
        log.info("Received enrich ticket request");
        final Ticket theTicket = request.getTicket();

        // Convert and normalize ticket attributes
        Struct convertedTicketAttributes = convertAndNormalizeStruct(theTicket.getTicketAttributes());

        // Convert and normalize player attributes
        List<Ticket.PlayerData> convertedPlayers = theTicket.getPlayersList().stream()
                .map(player -> Ticket.PlayerData.newBuilder()
                        .setPlayerId(player.getPlayerId())
                        .setAttributes(convertAndNormalizeStruct(player.getAttributes()))
                        .build())
                .collect(Collectors.toList());

        if (theTicket.getTicketAttributes().getFieldsCount() <= 0) {
            Map<String, Value> fields = new HashMap<>();
            fields.put("enrichedNumber", Value.newBuilder().setNumberValue(20).build());
            final Ticket newTicket = Ticket.newBuilder().setCreatedAt(theTicket.getCreatedAt())
                    .putAllLatencies(theTicket.getLatenciesMap()).setMatchPool(theTicket.getMatchPool())
                    .setNamespace(theTicket.getNamespace()).setPartySessionId(theTicket.getPartySessionId())
                    .addAllPlayers(convertedPlayers)
                    .setTicketAttributes(Struct.newBuilder().putAllFields(fields).build())
                    .setTicketId(theTicket.getTicketId()).build();
            final EnrichTicketResponse response = EnrichTicketResponse.newBuilder().setTicket(newTicket).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } else {
            final Ticket newTicket = Ticket.newBuilder()
                    .setCreatedAt(theTicket.getCreatedAt())
                    .putAllLatencies(theTicket.getLatenciesMap())
                    .setMatchPool(theTicket.getMatchPool())
                    .setNamespace(theTicket.getNamespace())
                    .setPartySessionId(theTicket.getPartySessionId())
                    .addAllPlayers(convertedPlayers)
                    .setTicketAttributes(convertedTicketAttributes)
                    .setTicketId(theTicket.getTicketId())
                    .build();
            final EnrichTicketResponse response = EnrichTicketResponse.newBuilder().setTicket(newTicket).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public StreamObserver<MakeMatchesRequest> makeMatches(StreamObserver<MatchResponse> responseObserver) {
        return new StreamObserver<>() {
            List<Ticket> unmatchedTickets = new ArrayList<>();

            @Override
            public void onNext(MakeMatchesRequest makeMatchesRequest) {
                log.info("Received make matches request");
                if (makeMatchesRequest.hasParameters()) {
                    long tickId = makeMatchesRequest.getParameters().getTickId();
                    log.info("Received makeMatchesRequest parameters with tickId: {}", tickId);
                    Rules rules = makeMatchesRequest.getParameters().getRules();
                    if (rules != null) {
                        try {
                            final Gson gson = new Gson();
                            final GameRules gameRules = gson.fromJson(rules.getJson(), GameRules.class);

                            // Validate rules
                            if (gameRules.getAlliance() != null && !gameRules.getAlliance().isValid()) {
                                log.error("Invalid alliance rule: minNumber > maxNumber or playerMinNumber > playerMaxNumber");
                                responseObserver.onError(new RuntimeException("Invalid alliance rule configuration"));
                                return;
                            }

                            if (gameRules.getShipCountMin() > gameRules.getShipCountMax()) {
                                log.error("Invalid ship count rule: ShipCountMax < ShipCountMin");
                                responseObserver.onError(new RuntimeException("ShipCountMax is less than ShipCountMin"));
                                return;
                            }

                            // Update rules
                            MatchmakingFunctionService.this.currentRules = gameRules;
                            MatchmakingFunctionService.this.shipCountMin = gameRules.getShipCountMin();
                            MatchmakingFunctionService.this.shipCountMax = gameRules.getShipCountMax();

                            log.info("Updated rules: {}", gameRules);
                            log.info("Calculated minPlayers: {}, maxPlayers: {}",
                                    gameRules.getMinPlayers(), gameRules.getMaxPlayers());

                        } catch (Exception e) {
                            log.error("Failed to parse rules JSON: {}", e.getMessage());
                            responseObserver.onError(new RuntimeException("Invalid rules JSON format"));
                            return;
                        }
                    }
                }
                if (makeMatchesRequest.hasTicket()) {
                    log.info("Received ticket");
                    final Ticket originalTicket = makeMatchesRequest.getTicket();
                    log.info("Processing ticket with ID: {} for makeMatches", originalTicket.getTicketId());

                    // Convert and normalize ticket attributes and player attributes
                    final Ticket convertedTicket = convertAndNormalizeTicket(originalTicket);
                    unmatchedTickets.add(convertedTicket);

                    // Use advanced rule processing
                    int minPlayers = currentRules != null ? currentRules.getMinPlayers() : shipCountMin;
                    int maxPlayers = currentRules != null ? currentRules.getMaxPlayers() : shipCountMax;

                    if (unmatchedTickets.size() >= maxPlayers) {
                        pushMatchResultAndClearUnmatchedTickets(responseObserver, unmatchedTickets, minPlayers, maxPlayers);
                    }
                    log.info("Unmatched tickets size: {}", unmatchedTickets.size());
                }
            }

            @Override
            public void onError(Throwable t) {
                log.warn("Make matches cancelled");
            }

            @Override
            public void onCompleted() {
                log.info("Complete, unmatched tickets size: {}", unmatchedTickets.size());
                int minPlayers = currentRules != null ? currentRules.getMinPlayers() : shipCountMin;
                if (unmatchedTickets.size() >= minPlayers) {
                    int maxPlayers = currentRules != null ? currentRules.getMaxPlayers() : shipCountMax;
                    pushMatchResultAndClearUnmatchedTickets(responseObserver, unmatchedTickets, minPlayers, maxPlayers);
                }
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public StreamObserver<BackfillMakeMatchesRequest> backfillMatches(
            StreamObserver<BackfillResponse> responseObserver) {
        return new StreamObserver<>() {
            private GameRules backfillRules = null;
            private List<Ticket> unmatchedTickets = new ArrayList<>();
            private List<BackfillTicket> unmatchedBackfillTickets = new ArrayList<>();

            @Override
            public void onNext(BackfillMakeMatchesRequest request) {
                log.info("Received backfill match request");
                
                if (request.hasParameters()) {
                    long tickId = request.getParameters().getTickId();
                    log.info("Received backfill request parameters with tickId: {}", tickId);
                    Rules rules = request.getParameters().getRules();
                    if (rules != null) {
                        try {
                            final Gson gson = new Gson();
                            backfillRules = gson.fromJson(rules.getJson(), GameRules.class);
                            log.info("Updated backfill rules: {}", backfillRules);
                        } catch (Exception e) {
                            log.error("Failed to parse backfill rules JSON: {}", e.getMessage());
                        }
                    }
                } else if (request.hasTicket()) {
                    log.info("Received backfill ticket");
                    final Ticket ticket = request.getTicket();
                    log.info("Processing ticket with ID: {} for backfillMatches", ticket.getTicketId());
                    final Ticket convertedTicket = convertAndNormalizeTicket(ticket);
                    unmatchedTickets.add(convertedTicket);
                    
                    // Try to create backfill proposals
                    createBackfillProposals(responseObserver);
                } else if (request.hasBackfillTicket()) {
                    log.info("Received backfill ticket");
                    final BackfillTicket backfillTicket = request.getBackfillTicket();
                    log.info("Processing backfill ticket with ID: {} for backfillMatches", backfillTicket.getTicketId());
                    final BackfillTicket convertedBackfillTicket = convertAndNormalizeBackfillTicket(backfillTicket);
                    unmatchedBackfillTickets.add(convertedBackfillTicket);
                    
                    // Try to create backfill proposals
                    createBackfillProposals(responseObserver);
                }
            }

            @Override
            public void onError(Throwable t) {
                log.warn("Backfill error or cancelled");
            }

            @Override
            public void onCompleted() {
                log.info("Backfill request completed");
            }

            private void createBackfillProposals(StreamObserver<BackfillResponse> responseObserver) {
                // Simple backfill logic: pair backfill tickets with regular tickets
                while (!unmatchedBackfillTickets.isEmpty() && !unmatchedTickets.isEmpty()) {
                    BackfillTicket backfillTicket = unmatchedBackfillTickets.remove(0);
                    Ticket ticket = unmatchedTickets.remove(0);
                    
                    BackfillProposal proposal = createBackfillProposal(backfillTicket, ticket);
                    BackfillResponse response = BackfillResponse.newBuilder()
                            .setBackfillProposal(proposal)
                            .build();
                    responseObserver.onNext(response);
                }
            }
        };
    }

    private void pushMatchResultAndClearUnmatchedTickets(StreamObserver<MatchResponse> responseObserver,
            List<Ticket> unmatchedTickets, int minPlayers, int maxPlayers) {
        final Match match = makeMatchFromUnmatchedTickets(unmatchedTickets, minPlayers, maxPlayers);
        responseObserver.onNext(MatchResponse.newBuilder().setMatch(match).build());
        unmatchedTickets.clear();
    }

    private Match makeMatchFromUnmatchedTickets(List<Ticket> unmatchedTickets, int minPlayers, int maxPlayers) {
        log.info("MATCHMAKER: seeing if we have enough tickets to match");

        // Calculate how many tickets to use for this match
        int numPlayers = minPlayers;
        if (unmatchedTickets.size() >= maxPlayers) {
            numPlayers = maxPlayers;
        }

        log.info("MATCHMAKER: I have enough tickets to match! Using {} tickets", numPlayers);

        // Take only the required number of tickets
        List<Ticket> ticketsToUse = unmatchedTickets.subList(0, numPlayers);

        final List<Ticket.PlayerData> playerDataList = new ArrayList<>();
        for (Ticket ticket : ticketsToUse) {
            List<Ticket.PlayerData> players = ticket.getPlayersList();
            if (players != null && !players.isEmpty()) {
                playerDataList.addAll(players);
            }
        }

        final List<String> playerIds = playerDataList.stream()
                .map(Ticket.PlayerData::getPlayerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<Ticket> safeCopy = ticketsToUse.stream()
                .filter(t -> t.getPlayersList() != null && !t.getPlayersList().isEmpty())
                .collect(Collectors.toList());

        // Determine if backfill should be enabled
        boolean backfill = false;
        if (currentRules != null && currentRules.isAutoBackfill() && numPlayers < maxPlayers) {
            backfill = true;
        }

        final String teamId = UUID.randomUUID().toString();
        Struct matchAttributes = Struct.newBuilder()
            .putFields("assignment", Value.newBuilder()
                .setStructValue(Struct.newBuilder()
                    .putFields("small-team-1", Value.newBuilder()
                        .setListValue(ListValue.newBuilder()
                                .addValues(Value.newBuilder().setStringValue(teamId).build())
                                .build())
                        .build())
                    .build())
                .build())
            .build();

        // Convert and normalize match attributes
        Struct convertedMatchAttributes = convertAndNormalizeStruct(matchAttributes);

        final Match match = Match.newBuilder()
                .addRegionPreferences("us-east-2")
                .addRegionPreferences("us-west-2")
                .addAllTickets(safeCopy)
                .addTeams(Match.Team.newBuilder()
                        .addAllUserIds(playerIds)
                        .setTeamId(teamId)
                        .build())
                .setMatchAttributes(convertedMatchAttributes)
                .setBackfill(backfill)
                .build();

        log.info("MATCHMAKER: sending to results channel");
        log.info("MATCHMAKER: reducing unmatched tickets {} to {}", unmatchedTickets.size(), unmatchedTickets.size() - numPlayers);

        return match;
    }

    /**
     * Converts and normalizes a protobuf Struct using JSON round-trip conversion.
     */
    private Struct convertAndNormalizeStruct(Struct struct) {
        if (struct == null || struct.getFieldsCount() == 0) {
            return struct;
        }

        try {
            // Convert protobuf Struct to JSON string
            String json = JsonFormat.printer().print(struct);

            // Parse JSON to Map and apply ConversionUtils
            Map<String, Object> structMap = new Gson().fromJson(json, Map.class);
            Map<String, Object> convertedMap = ConversionUtils.convertAttribute(structMap);

            // Convert back to JSON and then to protobuf Struct
            String convertedJson = new Gson().toJson(convertedMap);
            Struct.Builder structBuilder = Struct.newBuilder();
            JsonFormat.parser().merge(convertedJson, structBuilder);

            return structBuilder.build();

        } catch (Exception e) {
            log.error("Failed to convert and normalize struct: {}", e.getMessage());
            return struct; // Return original if conversion fails
        }
    }

    /**
     * Converts and normalizes a Ticket object, including its attributes and player attributes.
     */
    private Ticket convertAndNormalizeTicket(Ticket ticket) {
        if (ticket == null) {
            return ticket;
        }

        try {
            // Convert and normalize ticket attributes
            Struct convertedTicketAttributes = convertAndNormalizeStruct(ticket.getTicketAttributes());

            // Convert and normalize player attributes
            List<Ticket.PlayerData> convertedPlayers = ticket.getPlayersList().stream()
                    .map(player -> Ticket.PlayerData.newBuilder()
                            .setPlayerId(player.getPlayerId())
                            .setAttributes(convertAndNormalizeStruct(player.getAttributes()))
                            .build())
                    .collect(Collectors.toList());

            // Build the converted ticket
            return Ticket.newBuilder()
                    .setTicketId(ticket.getTicketId())
                    .setMatchPool(ticket.getMatchPool())
                    .setCreatedAt(ticket.getCreatedAt())
                    .addAllPlayers(convertedPlayers)
                    .setTicketAttributes(convertedTicketAttributes)
                    .putAllLatencies(ticket.getLatenciesMap())
                    .setPartySessionId(ticket.getPartySessionId())
                    .setNamespace(ticket.getNamespace())
                    .build();

        } catch (Exception e) {
            log.error("Failed to convert and normalize ticket: {}", e.getMessage());
            return ticket; // Return original if conversion fails
        }
    }

    /**
     * Converts and normalizes a BackfillTicket object.
     */
    private BackfillTicket convertAndNormalizeBackfillTicket(BackfillTicket backfillTicket) {
        if (backfillTicket == null) {
            return backfillTicket;
        }

        try {
            // Convert and normalize partial match
            BackfillTicket.PartialMatch convertedPartialMatch = convertAndNormalizePartialMatch(backfillTicket.getPartialMatch());

            return BackfillTicket.newBuilder()
                    .setTicketId(backfillTicket.getTicketId())
                    .setMatchPool(backfillTicket.getMatchPool())
                    .setCreatedAt(backfillTicket.getCreatedAt())
                    .setPartialMatch(convertedPartialMatch)
                    .setMatchSessionId(backfillTicket.getMatchSessionId())
                    .build();

        } catch (Exception e) {
            log.error("Failed to convert and normalize backfill ticket: {}", e.getMessage());
            return backfillTicket; // Return original if conversion fails
        }
    }

    /**
     * Converts and normalizes a PartialMatch object.
     */
    private BackfillTicket.PartialMatch convertAndNormalizePartialMatch(BackfillTicket.PartialMatch partialMatch) {
        if (partialMatch == null) {
            return partialMatch;
        }

        try {
            // Convert and normalize tickets
            List<Ticket> convertedTickets = partialMatch.getTicketsList().stream()
                    .map(this::convertAndNormalizeTicket)
                    .collect(Collectors.toList());

            // Convert and normalize teams
            List<BackfillTicket.Team> convertedTeams = partialMatch.getTeamsList().stream()
                    .map(this::convertAndNormalizeBackfillTeam)
                    .collect(Collectors.toList());

            // Convert and normalize match attributes
            Struct convertedMatchAttributes = convertAndNormalizeStruct(partialMatch.getMatchAttributes());

            return BackfillTicket.PartialMatch.newBuilder()
                    .addAllTickets(convertedTickets)
                    .addAllTeams(convertedTeams)
                    .addAllRegionPreferences(partialMatch.getRegionPreferencesList())
                    .setMatchAttributes(convertedMatchAttributes)
                    .setBackfill(partialMatch.getBackfill())
                    .setServerName(partialMatch.getServerName())
                    .setClientVersion(partialMatch.getClientVersion())
                    .build();

        } catch (Exception e) {
            log.error("Failed to convert and normalize partial match: {}", e.getMessage());
            return partialMatch; // Return original if conversion fails
        }
    }

    /**
     * Converts and normalizes a BackfillTicket.Team object.
     */
    private BackfillTicket.Team convertAndNormalizeBackfillTeam(BackfillTicket.Team team) {
        if (team == null) {
            return team;
        }

        return BackfillTicket.Team.newBuilder()
                .addAllUserIds(team.getUserIdsList())
                .addAllParties(team.getPartiesList())
                .setTeamId(team.getTeamId())
                .build();
    }

    /**
     * Creates a backfill proposal from a backfill ticket and a regular ticket.
     */
    private BackfillProposal createBackfillProposal(BackfillTicket backfillTicket, Ticket ticket) {
        // Create a new team for the added ticket
        List<String> playerIds = ticket.getPlayersList().stream()
                .map(Ticket.PlayerData::getPlayerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        String newTeamId = UUID.randomUUID().toString();
        BackfillProposal.Team newTeam = BackfillProposal.Team.newBuilder()
                .addAllUserIds(playerIds)
                .setTeamId(newTeamId)
                .build();

        // Combine existing teams with the new team
        List<BackfillProposal.Team> proposedTeams = new ArrayList<>();
        proposedTeams.addAll(backfillTicket.getPartialMatch().getTeamsList().stream()
                .map(team -> {
                    // Use existing team ID or generate a new one if not present
                    String existingTeamId = team.getTeamId();
                    if (existingTeamId == null || existingTeamId.isEmpty()) {
                        existingTeamId = UUID.randomUUID().toString();
                    }
                    return BackfillProposal.Team.newBuilder()
                            .addAllUserIds(team.getUserIdsList())
                            .addAllParties(team.getPartiesList())
                            .setTeamId(existingTeamId)
                            .build();
                })
                .collect(Collectors.toList()));
        proposedTeams.add(newTeam);

        return BackfillProposal.newBuilder()
                .setBackfillTicketId(backfillTicket.getTicketId())
                .setCreatedAt(backfillTicket.getCreatedAt())
                .addAddedTickets(ticket)
                .addAllProposedTeams(proposedTeams)
                .setProposalId(UUID.randomUUID().toString())
                .setMatchPool(backfillTicket.getMatchPool())
                .setMatchSessionId(backfillTicket.getMatchSessionId())
                .build();
    }
}
