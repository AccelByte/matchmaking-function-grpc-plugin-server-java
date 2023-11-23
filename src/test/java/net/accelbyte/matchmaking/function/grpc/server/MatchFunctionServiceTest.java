package net.accelbyte.matchmaking.function.grpc.server;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import net.accelbyte.matchmakingv2.matchfunction.GetStatCodesRequest;
import net.accelbyte.matchmakingv2.matchfunction.MakeMatchesRequest;
import net.accelbyte.matchmakingv2.matchfunction.Match;
import net.accelbyte.matchmakingv2.matchfunction.MatchFunctionGrpc;
import net.accelbyte.matchmakingv2.matchfunction.MatchResponse;
import net.accelbyte.matchmakingv2.matchfunction.Ticket;
import net.accelbyte.matchmakingv2.matchfunction.ValidateTicketRequest;
import net.accelbyte.matchmakingv2.matchfunction.ValidateTicketResponse;
import net.accelbyte.sdk.core.AccelByteSDK;
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
class MatchFunctionServiceTest {
        private ManagedChannel channel;

        private final Metadata header = new Metadata();

        @LocalRunningGrpcPort
        int port;

        @Autowired
        AccelByteSDK sdk;

        @BeforeEach
        private void init() {
                channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();

                Metadata.Key<String> key = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
                header.put(key, "Bearer abc");
        }

        @Test
        void validateTicket() {
                Mockito.reset(sdk);
                Mockito.when(sdk.validateToken(any(), any(), anyInt())).thenReturn(true);

                final ValidateTicketResponse validateTicketResponse = MatchFunctionGrpc.newBlockingStub(channel)
                                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(header))
                                .validateTicket(ValidateTicketRequest.newBuilder().build());

                assertTrue(validateTicketResponse.getValidTicket());
        }

        @Test
        void makeMatches() throws InterruptedException {
                Mockito.reset(sdk);
                Mockito.when(sdk.validateToken(any(), any(), anyInt())).thenReturn(true);

                final MakeMatchesRequest makeMatchesRequest1 = MakeMatchesRequest.newBuilder()
                                .setTicket(Ticket.newBuilder().setTicketId("ad74df0e")
                                                .addPlayers(Ticket.PlayerData.newBuilder().setPlayerId("111").build())
                                                .build())
                                .build();

                final MakeMatchesRequest makeMatchesRequest2 = MakeMatchesRequest.newBuilder()
                                .setTicket(Ticket.newBuilder().setTicketId("34f0765fa6ba")
                                                .addPlayers(Ticket.PlayerData.newBuilder().setPlayerId("222").build())
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
                Mockito.reset(sdk);
                Mockito.when(sdk.validateToken(any(), any(), anyInt())).thenReturn(false);

                Assertions.assertThrows(StatusRuntimeException.class, () -> {
                        MatchFunctionGrpc.newBlockingStub(channel)
                                        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(header))
                                        .getStatCodes(GetStatCodesRequest.newBuilder().build());
                });
        }
}