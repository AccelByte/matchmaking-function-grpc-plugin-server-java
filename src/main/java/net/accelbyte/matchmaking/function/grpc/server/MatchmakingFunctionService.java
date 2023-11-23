package net.accelbyte.matchmaking.function.grpc.server;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import lombok.extern.slf4j.Slf4j;

import com.google.gson.Gson;
import net.accelbyte.matchmakingv2.matchfunction.*;
import io.grpc.stub.StreamObserver;
import org.lognet.springboot.grpc.GRpcService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@GRpcService
public class MatchmakingFunctionService extends MatchFunctionGrpc.MatchFunctionImplBase {
    private final List<Ticket> unmatchedTickets = new ArrayList<>();
    private int shipCountMin = 2;
    private int shipCountMax = 2;

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
        if (theTicket.getTicketAttributes().getFieldsCount() <= 0) {
            Map<String, Value> fields = new HashMap<>();
            fields.put("enrichedNumber", Value.newBuilder().setNumberValue(20).build());
            final Ticket newTicket = Ticket.newBuilder().setCreatedAt(theTicket.getCreatedAt())
                    .putAllLatencies(theTicket.getLatenciesMap()).setMatchPool(theTicket.getMatchPool())
                    .setNamespace(theTicket.getNamespace()).setPartySessionId(theTicket.getPartySessionId())
                    .addAllPlayers(theTicket.getPlayersList())
                    .setTicketAttributes(Struct.newBuilder().putAllFields(fields).build())
                    .setTicketId(theTicket.getTicketId()).build();
            final EnrichTicketResponse response = EnrichTicketResponse.newBuilder().setTicket(newTicket).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } else {
            final EnrichTicketResponse response = EnrichTicketResponse.newBuilder().setTicket(theTicket).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public StreamObserver<MakeMatchesRequest> makeMatches(StreamObserver<MatchResponse> responseObserver) {
        return new StreamObserver<>() {
            @Override
            public void onNext(MakeMatchesRequest makeMatchesRequest) {
                log.info("Received make matches request");
                if (makeMatchesRequest.hasParameters()) {
                    log.info("Received parameters");
                    Rules rules = makeMatchesRequest.getParameters().getRules();
                    if (rules != null) {
                        final Gson gson = new Gson();
                        final RuleObject ruleObject = gson.fromJson(rules.getJson(), RuleObject.class);
                        final int newShipCountMin = ruleObject.getShipCountMin();
                        final int newShipCountMax = ruleObject.getShipCountMax();
                        if (newShipCountMin != 0 && newShipCountMax != 0 && newShipCountMin <= newShipCountMax) {
                            MatchmakingFunctionService.this.shipCountMin = newShipCountMin;
                            MatchmakingFunctionService.this.shipCountMax = newShipCountMax;
                            log.info("Updated shipCountMin: {}, shipCountMax: {}",
                                    MatchmakingFunctionService.this.shipCountMin,
                                    MatchmakingFunctionService.this.shipCountMax);
                        }
                    }
                }
                if (makeMatchesRequest.hasTicket()) {
                    log.info("Received ticket");
                    final Ticket newTicket = makeMatchesRequest.getTicket();
                    unmatchedTickets.add(newTicket);
                    if (unmatchedTickets.size() == shipCountMax) {
                        pushMatchResultAndClearUnmatchedTickets(responseObserver);
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
                if (unmatchedTickets.size() >= shipCountMin) {
                    pushMatchResultAndClearUnmatchedTickets(responseObserver);
                }
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public StreamObserver<BackfillMakeMatchesRequest> backfillMatches(
            StreamObserver<BackfillResponse> responseObserver) {
        return new StreamObserver<>() {
            @Override
            public void onNext(BackfillMakeMatchesRequest value) {
                log.info("Received backfill match request");
                BackfillResponse response = BackfillResponse.newBuilder()
                        .setBackfillProposal(BackfillProposal.newBuilder().build()).build();
                responseObserver.onNext(response);
            }

            @Override
            public void onError(Throwable t) {
                log.warn("Backfill error or cancelled");
            }

            @Override
            public void onCompleted() {
                log.info("Backfill request completed");
            }
        };
    }

    private void pushMatchResultAndClearUnmatchedTickets(StreamObserver<MatchResponse> responseObserver) {
        final Match match = makeMatchFromUnmatchedTickets();
        responseObserver.onNext(MatchResponse.newBuilder().setMatch(match).build());
        unmatchedTickets.clear();
    }

    private Match makeMatchFromUnmatchedTickets() {
        final List<Ticket.PlayerData> playerDataList = new ArrayList<>();
        for (int i = 0; i < unmatchedTickets.size(); i++) {
            playerDataList.addAll(unmatchedTickets.get(i).getPlayersList());
        }
        final List<String> playerIds = playerDataList.stream().map(e -> e.getPlayerId()).collect(Collectors.toList());
        final Match match = Match.newBuilder().addRegionPreferences("any").addAllTickets(unmatchedTickets)
                .addTeams(Match.Team.newBuilder().addAllUserIds(playerIds).build()).build();
        return match;
    }
}
