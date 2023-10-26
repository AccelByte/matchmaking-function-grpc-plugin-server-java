package net.accelbyte.service;

import com.google.common.reflect.TypeToken;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import lombok.extern.slf4j.Slf4j;

import com.google.gson.Gson;
import net.accelbyte.matchmakingv2.matchfunction.*;
import net.accelbyte.object.RuleObject;
import io.grpc.stub.StreamObserver;
import org.lognet.springboot.grpc.GRpcService;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@GRpcService
public class MatchFunctionService extends MatchFunctionGrpc.MatchFunctionImplBase {
    private final List<Ticket> unmatchedTickets = new ArrayList<>();

    private int shipCountMin = 2;
    private int shipCountMax = 2;

    @Override
    public void getStatCodes(GetStatCodesRequest request, StreamObserver<StatCodesResponse> responseObserver) {
        log.info("Received get stat codes request");

        Type listType = new TypeToken<List<String>>() {}.getType();
        final String jsonRules = request.getRules().getJson();

        Gson gson = new Gson();
        List<String> ruleList = gson.fromJson(jsonRules, listType);

        StatCodesResponse response = StatCodesResponse.newBuilder()
                .addAllCodes(ruleList)
                .build();
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

        Ticket theTicket = request.getTicket();
        if (theTicket.getTicketAttributes().getFieldsCount() <= 0) {
            Map<String, Value> fields = new HashMap<>();
            fields.put("enrichedNumber", Value.newBuilder()
                    .setNumberValue(20)
                    .build());

            Ticket newTicket = Ticket.newBuilder()
                    .setCreatedAt(theTicket.getCreatedAt())
                    .putAllLatencies(theTicket.getLatenciesMap())
                    .setMatchPool(theTicket.getMatchPool())
                    .setNamespace(theTicket.getNamespace())
                    .setPartySessionId(theTicket.getPartySessionId())
                    .addAllPlayers(theTicket.getPlayersList())
                    .setTicketAttributes(Struct.newBuilder()
                            .putAllFields(fields)
                            .build())
                    .setTicketId(theTicket.getTicketId())
                    .build();

            EnrichTicketResponse response = EnrichTicketResponse.newBuilder()
                    .setTicket(newTicket)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } else {

            EnrichTicketResponse response = EnrichTicketResponse.newBuilder()
                    .setTicket(theTicket)
                    .build();

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
                        if (newShipCountMin != 0 && newShipCountMax != 0
                                && newShipCountMin <= newShipCountMax) {
                            MatchFunctionService.this.shipCountMin = newShipCountMin;
                            MatchFunctionService.this.shipCountMax = newShipCountMax;
                            log.info("Updated shipCountMin= " + MatchFunctionService.this.shipCountMin
                                    + " and shipCountMax= " + MatchFunctionService.this.shipCountMax);
                        }
                    }
                }

                if (makeMatchesRequest.hasTicket()) {
                    log.info("Received ticket");
                    final Ticket newTicket = makeMatchesRequest.getTicket();
                    unmatchedTickets.add(newTicket);
                    if (unmatchedTickets.size() == shipCountMax) {
                        createAndPushMatchResultAndClearUnmatchedTickets(responseObserver);
                    }
                    log.info("Unmatched tickets size: " + unmatchedTickets.size());
                }
            }

            @Override
            public void onError(Throwable t) {
                log.warn("Make matches cancelled");
            }

            @Override
            public void onCompleted() {
                log.info("Complete, unmatched tickets size: " + unmatchedTickets.size());
                if (unmatchedTickets.size() >= shipCountMin) {
                    createAndPushMatchResultAndClearUnmatchedTickets(responseObserver);
                }
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public StreamObserver<BackfillMakeMatchesRequest> backfillMatches(StreamObserver<BackfillResponse> responseObserver) {
        return new StreamObserver<>() {
            @Override
            public void onNext(BackfillMakeMatchesRequest value) {
                log.info("received backfill match request");

                BackfillResponse response = BackfillResponse.newBuilder()
                        .setBackfillProposal(BackfillProposal.newBuilder()
                                .build())
                        .build();

                responseObserver.onNext(response);
            }

            @Override
            public void onError(Throwable t) {
                log.warn("backfill error or cancelled");
            }

            @Override
            public void onCompleted() {
                log.info("backfill request completed");
            }
        };
    }

    private void createAndPushMatchResultAndClearUnmatchedTickets(StreamObserver<MatchResponse> responseObserver) {
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
        final Match match = Match.newBuilder()
                .addRegionPreferences("any")
                .addAllTickets(unmatchedTickets)
                .addTeams(Match.Team.newBuilder().addAllUserIds(playerIds).build())
                .build();
        return match;
    }
}
