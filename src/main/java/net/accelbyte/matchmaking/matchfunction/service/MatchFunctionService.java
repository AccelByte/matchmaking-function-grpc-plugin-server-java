package net.accelbyte.matchmaking.matchfunction.service;

import net.accelbyte.matchmaking.matchfunction.object.RuleObject;
import com.google.gson.Gson;
import io.grpc.examples.matchfunctiongrpc.GetStatCodesRequest;
import io.grpc.examples.matchfunctiongrpc.MakeMatchesRequest;
import io.grpc.examples.matchfunctiongrpc.Match;
import io.grpc.examples.matchfunctiongrpc.MatchFunctionGrpc;
import io.grpc.examples.matchfunctiongrpc.MatchResponse;
import io.grpc.examples.matchfunctiongrpc.Rules;
import io.grpc.examples.matchfunctiongrpc.StatCodesResponse;
import io.grpc.examples.matchfunctiongrpc.Ticket;
import io.grpc.examples.matchfunctiongrpc.ValidateTicketRequest;
import io.grpc.examples.matchfunctiongrpc.ValidateTicketResponse;
import io.grpc.stub.StreamObserver;
import org.lognet.springboot.grpc.GRpcService;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@GRpcService
public class MatchFunctionService extends MatchFunctionGrpc.MatchFunctionImplBase {

    private static final Logger logger = Logger.getLogger(MatchFunctionService.class.getName());

    private final List<Ticket> unmatchedTickets = new ArrayList<>();
    
    private int shipCountMin = 2;
    private int shipCountMax = 2;

    @Override
    public void getStatCodes(GetStatCodesRequest request, StreamObserver<StatCodesResponse> responseObserver) {
        StatCodesResponse response = StatCodesResponse.newBuilder().build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void validateTicket(ValidateTicketRequest request, StreamObserver<ValidateTicketResponse> responseObserver) {
        ValidateTicketResponse response = ValidateTicketResponse.newBuilder().setValid(true).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<MakeMatchesRequest> makeMatches(StreamObserver<MatchResponse> responseObserver) {

        return new StreamObserver<>() {
            @Override
            public void onNext(MakeMatchesRequest makeMatchesRequest) {
                if(makeMatchesRequest.hasParameters()) {
                    logger.info("received parameters");
                    Rules rules = makeMatchesRequest.getParameters().getRules();
                    if (rules != null) {
                        Gson gson = new Gson();
                        RuleObject ruleObject = gson.fromJson(rules.getJson(), RuleObject.class);
                        int newShipCountMin = ruleObject.getShipCountMin();
                        int newShipCountMax = ruleObject.getShipCountMax();
                        if(newShipCountMin != 0 && newShipCountMax != 0
                        && newShipCountMin <= newShipCountMax){
                            MatchFunctionService.this.shipCountMin = newShipCountMin;
                            MatchFunctionService.this.shipCountMax = newShipCountMax;
                            logger.info("updated shipCountMin= "+ MatchFunctionService.this.shipCountMin +" and shipCountMax= " + MatchFunctionService.this.shipCountMax);
                        }
                    }
                }

                if(makeMatchesRequest.hasTicket()){
                    logger.info("received ticket");

                    Ticket newTicket = makeMatchesRequest.getTicket();
                    unmatchedTickets.add(newTicket);
                    if(unmatchedTickets.size() == shipCountMax) {
                        createAndPushMatchResultAndClearUnmatchedTickets(responseObserver);
                    }
                    logger.info("unmatched tickets size: " + unmatchedTickets.size());
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.log(Level.WARNING, "makeMatches cancelled");
            }

            @Override
            public void onCompleted() {
                logger.info("on complete. unmatched tickets size: " + unmatchedTickets.size());
                if(unmatchedTickets.size() >= shipCountMin) {
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
