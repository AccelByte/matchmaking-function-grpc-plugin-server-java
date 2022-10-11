package net.accelbyte.matchmaking.matchfunction.service;

import lombok.extern.slf4j.Slf4j;
import net.accelbyte.matchmaking.matchfunction.object.RuleObject;
import com.google.gson.Gson;
import net.accelbyte.matchmaking.matchfunction.grpc.GetStatCodesRequest;
import net.accelbyte.matchmaking.matchfunction.grpc.MakeMatchesRequest;
import net.accelbyte.matchmaking.matchfunction.grpc.Match;
import net.accelbyte.matchmaking.matchfunction.grpc.MatchFunctionGrpc;
import net.accelbyte.matchmaking.matchfunction.grpc.MatchResponse;
import net.accelbyte.matchmaking.matchfunction.grpc.Rules;
import net.accelbyte.matchmaking.matchfunction.grpc.StatCodesResponse;
import net.accelbyte.matchmaking.matchfunction.grpc.Ticket;
import net.accelbyte.matchmaking.matchfunction.grpc.ValidateTicketRequest;
import net.accelbyte.matchmaking.matchfunction.grpc.ValidateTicketResponse;
import io.grpc.stub.StreamObserver;
import org.lognet.springboot.grpc.GRpcService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@GRpcService
public class MatchFunctionService extends MatchFunctionGrpc.MatchFunctionImplBase {
    private final List<Ticket> unmatchedTickets = new ArrayList<>();

    private int shipCountMin = 2;
    private int shipCountMax = 2;

    @Override
    public void getStatCodes(GetStatCodesRequest request, StreamObserver<StatCodesResponse> responseObserver) {
        log.info("received getStatCodes request.");
        StatCodesResponse response = StatCodesResponse.newBuilder().build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void validateTicket(ValidateTicketRequest request, StreamObserver<ValidateTicketResponse> responseObserver) {
        log.info("received validateTicket request.");
        ValidateTicketResponse response = ValidateTicketResponse.newBuilder().setValid(true).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<MakeMatchesRequest> makeMatches(StreamObserver<MatchResponse> responseObserver) {

        return new StreamObserver<>() {
            @Override
            public void onNext(MakeMatchesRequest makeMatchesRequest) {
                log.info("received make matches request.");
                if (makeMatchesRequest.hasParameters()) {
                    log.info("received parameters");
                    Rules rules = makeMatchesRequest.getParameters().getRules();
                    if (rules != null) {
                        Gson gson = new Gson();
                        RuleObject ruleObject = gson.fromJson(rules.getJson(), RuleObject.class);
                        int newShipCountMin = ruleObject.getShipCountMin();
                        int newShipCountMax = ruleObject.getShipCountMax();
                        if (newShipCountMin != 0 && newShipCountMax != 0
                                && newShipCountMin <= newShipCountMax) {
                            MatchFunctionService.this.shipCountMin = newShipCountMin;
                            MatchFunctionService.this.shipCountMax = newShipCountMax;
                            log.info("updated shipCountMin= " + MatchFunctionService.this.shipCountMin
                                    + " and shipCountMax= " + MatchFunctionService.this.shipCountMax);
                        }
                    }
                }

                if (makeMatchesRequest.hasTicket()) {
                    log.info("received ticket");

                    Ticket newTicket = makeMatchesRequest.getTicket();
                    unmatchedTickets.add(newTicket);
                    if (unmatchedTickets.size() == shipCountMax) {
                        createAndPushMatchResultAndClearUnmatchedTickets(responseObserver);
                    }
                    log.info("unmatched tickets size: " + unmatchedTickets.size());
                }
            }

            @Override
            public void onError(Throwable t) {
                log.warn("makeMatches cancelled");
            }

            @Override
            public void onCompleted() {
                log.info("on complete. unmatched tickets size: " + unmatchedTickets.size());
                if (unmatchedTickets.size() >= shipCountMin) {
                    createAndPushMatchResultAndClearUnmatchedTickets(responseObserver);
                }
                responseObserver.onCompleted();
            }
        };
    }

    private void createAndPushMatchResultAndClearUnmatchedTickets(StreamObserver<MatchResponse> responseObserver) {
        Match match = makeMatchFromUnmatchedTickets();
        responseObserver.onNext(MatchResponse.newBuilder().setMatch(match).build());
        unmatchedTickets.clear();
    }

    private Match makeMatchFromUnmatchedTickets() {
        List<Ticket.PlayerData> playerDataList = new ArrayList<>();
        for (int i = 0; i < unmatchedTickets.size(); i++) {
            playerDataList.addAll(unmatchedTickets.get(i).getPlayersList());
        }
        List<String> playerIds = playerDataList.stream().map(e -> e.getPlayerId()).collect(Collectors.toList());
        Match match = Match.newBuilder()
                .addRegionPreferences("any")
                .addAllTickets(unmatchedTickets)
                .addTeams(Match.Team.newBuilder().addAllUserIds(playerIds).build())
                .build();
        return match;
    }
}
