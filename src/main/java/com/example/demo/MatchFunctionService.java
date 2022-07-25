package com.example.demo;

import io.grpc.examples.matchfunctiongrpc.GetStatCodesRequest;
import io.grpc.examples.matchfunctiongrpc.MakeMatchesRequest;
import io.grpc.examples.matchfunctiongrpc.Match;
import io.grpc.examples.matchfunctiongrpc.MatchFunctionGrpc;
import io.grpc.examples.matchfunctiongrpc.MatchResponse;
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

    private List<Ticket> unmatchedTickets = new ArrayList<>();

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

        return new StreamObserver<MakeMatchesRequest>() {
            @Override
            public void onNext(MakeMatchesRequest makeMatchesRequest) {

                if(makeMatchesRequest.getTicket() != null){

                    Ticket newTicket = makeMatchesRequest.getTicket();
                    unmatchedTickets.add(newTicket);

                    if(unmatchedTickets.size() == 2) {
                        List<Ticket.PlayerData> playerDataList = new ArrayList<>();
                        playerDataList.addAll(unmatchedTickets.get(0).getPlayersList());
                        playerDataList.addAll(unmatchedTickets.get(1).getPlayersList());

                        List<String> playerIds = playerDataList.stream().map(e -> e.getPlayerId()).collect(Collectors.toList());

                        Match match = Match.newBuilder()
                                .addRegionPreferences("any")
                                .addAllTickets(unmatchedTickets)
                                .addTeams(Match.Team.newBuilder().addAllUserIds(playerIds).build())
                                .build();

                        responseObserver.onNext(MatchResponse.newBuilder().setMatch(match).build());
                        unmatchedTickets.clear();

                    }
                }

            }

            @Override
            public void onError(Throwable t) {
                logger.log(Level.WARNING, "makeMatches cancelled");
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }
}
