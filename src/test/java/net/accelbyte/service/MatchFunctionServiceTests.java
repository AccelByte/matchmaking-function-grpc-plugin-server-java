package net.accelbyte.service;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import net.accelbyte.matchmaking.matchfunction.grpc.GetStatCodesRequest;
import net.accelbyte.matchmaking.matchfunction.grpc.MakeMatchesRequest;
import net.accelbyte.matchmaking.matchfunction.grpc.Match;
import net.accelbyte.matchmaking.matchfunction.grpc.MatchFunctionGrpc;
import net.accelbyte.matchmaking.matchfunction.grpc.MatchResponse;
import net.accelbyte.matchmaking.matchfunction.grpc.StatCodesResponse;
import net.accelbyte.matchmaking.matchfunction.grpc.Ticket;
import net.accelbyte.matchmaking.matchfunction.grpc.ValidateTicketRequest;
import net.accelbyte.matchmaking.matchfunction.grpc.ValidateTicketResponse;
import net.accelbyte.util.ServerAuthProvider;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lognet.springboot.grpc.context.LocalRunningGrpcPort;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

@Slf4j
@ActiveProfiles("test")
@SpringBootTest(properties = "grpc.port=0")
class MatchFunctionServiceTests {
    private ManagedChannel channel;

    private final Metadata header = new Metadata();

    @LocalRunningGrpcPort
    int port;

    @Autowired
    ServerAuthProvider authProvider;

    @BeforeEach
    private void init() {
        channel = ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .build();

        Metadata.Key<String> key = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
        header.put(key, "Bearer abc");
    }

    @Test
    void getStatCodes() {
        Mockito.reset(authProvider);
        Mockito.when(authProvider.validate(any(), any(), anyInt())).thenReturn(true);

        final StatCodesResponse statCodesResponse = MatchFunctionGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(header))
                .getStatCodes(GetStatCodesRequest.newBuilder().build());

        assertEquals(0, statCodesResponse.getCodesList().size());
    }

    @Test
    void validateTicket() {
        Mockito.reset(authProvider);
        Mockito.when(authProvider.validate(any(), any(), anyInt())).thenReturn(true);

        final ValidateTicketResponse validateTicketResponse = MatchFunctionGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(header))
                .validateTicket(ValidateTicketRequest.newBuilder().build());

        assertTrue(validateTicketResponse.getValid());
    }

    @Test
    void makeMatches() throws InterruptedException {
        Mockito.reset(authProvider);
        Mockito.when(authProvider.validate(any(), any(), anyInt())).thenReturn(true);

        final MakeMatchesRequest makeMatchesRequest1 = MakeMatchesRequest.newBuilder()
                .setTicket(Ticket.newBuilder()
                        .setTicketId("ad74df0e")
                        .addPlayers(Ticket.PlayerData.newBuilder()
                                .setPlayerId("111")
                                .build())
                        .build())
                .build();

        final MakeMatchesRequest makeMatchesRequest2 = MakeMatchesRequest.newBuilder()
                .setTicket(Ticket.newBuilder()
                        .setTicketId("34f0765fa6ba")
                        .addPlayers(Ticket.PlayerData.newBuilder()
                                .setPlayerId("222")
                                .build())
                        .build())
                .build();

        final List<Match> matchesReturned = new ArrayList<>();
        final CountDownLatch allRequestsDelivered = new CountDownLatch(1);
        final MatchFunctionGrpc.MatchFunctionStub matchFunctionAsyncStub = MatchFunctionGrpc.newStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(header));

        final StreamObserver<MakeMatchesRequest> makeMatchesRequestStreamObserver = matchFunctionAsyncStub
                .makeMatches(new StreamObserver<>() {
                    @Override
                    public void onNext(MatchResponse matchResponse) {
                        log.info("Received match response: " + matchResponse.getMatch());
                        matchesReturned.add(matchResponse.getMatch());
                    }

                    @Override
                    public void onError(Throwable t) {
                        log.warn("Make match failed");
                    }

                    @Override
                    public void onCompleted() {
                        log.info("Make match completed");
                        allRequestsDelivered.countDown();
                    }
                });

        makeMatchesRequestStreamObserver.onNext(makeMatchesRequest1);
        makeMatchesRequestStreamObserver.onNext(makeMatchesRequest2);
        makeMatchesRequestStreamObserver.onCompleted();

        assertTrue(allRequestsDelivered.await(1, TimeUnit.SECONDS));
        log.info("Returned match: " + matchesReturned);
        assertEquals(2, matchesReturned.get(0).getTeams(0).getUserIdsList().size());
    }

    @Test
    void failsAuthorization() {
        Mockito.reset(authProvider);
        Mockito.when(authProvider.validate(any(), any(), anyInt())).thenReturn(false);

        Assertions.assertThrows(StatusRuntimeException.class, () -> {
            MatchFunctionGrpc.newBlockingStub(channel)
                    .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(header))
                    .getStatCodes(GetStatCodesRequest.newBuilder().build());
        });
    }
}