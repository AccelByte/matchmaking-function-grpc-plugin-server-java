package net.accelbyte.matchmaking.matchfunction;

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
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import net.accelbyte.platform.exception.TokenIsExpiredException;
import net.accelbyte.platform.security.OAuthToken;
import net.accelbyte.platform.security.Permission;
import net.accelbyte.platform.security.service.OAuthService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lognet.springboot.grpc.context.LocalRunningGrpcPort;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ActiveProfiles("test")
@SpringBootTest(properties = "grpc.port=0")
class MatchFunctionServiceTest {

    private static final Logger logger = Logger.getLogger(MatchFunctionServiceTest.class.getName());

    private ManagedChannel channel;

    private final Metadata header = new Metadata();

    @LocalRunningGrpcPort
    int port;

    @Autowired
    OAuthService oAuthService;

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
        Mockito.reset(oAuthService);
        OAuthToken oAuthToken = new OAuthToken();
        Mockito.when(oAuthService.getOAuthToken(any())).thenReturn(oAuthToken);
        Mockito.when(oAuthService.validateTokenPermission(any(), any(), eq("accelbyte"), eq(null))).thenReturn(true);

        StatCodesResponse statCodesResponse = MatchFunctionGrpc.newBlockingStub(channel).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(header))
                .getStatCodes(GetStatCodesRequest.newBuilder().build());

        assertEquals(0, statCodesResponse.getCodesList().size());
    }



    @Test
    void validateTicket() {
        Mockito.reset(oAuthService);
        Mockito.when(oAuthService.getOAuthToken(any())).thenReturn(new OAuthToken());
        Mockito.when(oAuthService.validateTokenPermission(any(), any(), eq("accelbyte"), eq(null))).thenReturn(true);

        ValidateTicketResponse validateTicketResponse = MatchFunctionGrpc.newBlockingStub(channel).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(header))
                .validateTicket(ValidateTicketRequest.newBuilder().build());

        assertTrue(validateTicketResponse.getValid());
    }

    @Test
    void makeMatches() throws InterruptedException {
        Mockito.reset(oAuthService);
        Mockito.when(oAuthService.getOAuthToken(any())).thenReturn(new OAuthToken());
        Mockito.when(oAuthService.validateTokenPermission(any(), any(), eq("accelbyte"), eq(null))).thenReturn(true);

        MakeMatchesRequest makeMatchesRequest1 = MakeMatchesRequest.newBuilder()
                .setTicket(Ticket.newBuilder()
                        .setTicketId("ad74df0e")
                        .addPlayers(Ticket.PlayerData.newBuilder()
                                .setPlayerId("111")
                                .build())
                        .build())
                .build();

        MakeMatchesRequest makeMatchesRequest2 = MakeMatchesRequest.newBuilder()
                .setTicket(Ticket.newBuilder()
                        .setTicketId("34f0765fa6ba")
                        .addPlayers(Ticket.PlayerData.newBuilder()
                                .setPlayerId("222")
                                .build())
                        .build())
                .build();

        final List<Match> matchesReturned = new ArrayList<>();
        final CountDownLatch allRequestsDelivered = new CountDownLatch(1);
        MatchFunctionGrpc.MatchFunctionStub matchFunctionAsyncStub = MatchFunctionGrpc.newStub(channel).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(header));

        StreamObserver<MakeMatchesRequest> makeMatchesRequestStreamObserver = matchFunctionAsyncStub.makeMatches(new StreamObserver<>() {

            @Override
            public void onNext(MatchResponse matchResponse) {
                logger.info("received match response" + matchResponse.getMatch());
                matchesReturned.add(matchResponse.getMatch());
            }

            @Override
            public void onError(Throwable t) {
                logger.warning("make match failed");
            }

            @Override
            public void onCompleted() {
                logger.info("make match completed");
                allRequestsDelivered.countDown();
            }
        });

        makeMatchesRequestStreamObserver.onNext(makeMatchesRequest1);
        makeMatchesRequestStreamObserver.onNext(makeMatchesRequest2);
        makeMatchesRequestStreamObserver.onCompleted();

        assertTrue(allRequestsDelivered.await(1, TimeUnit.SECONDS));
        logger.info("returned match: " + matchesReturned);
        assertEquals(2, matchesReturned.get(0).getTeams(0).getUserIdsList().size());
    }

    @Test
    void failsAuthorization() {
        Mockito.reset(oAuthService);
        Mockito.when(oAuthService.getOAuthToken(any())).thenThrow(TokenIsExpiredException.class);

        StatusRuntimeException thrown = Assertions.assertThrows(StatusRuntimeException.class, () -> {
            StatCodesResponse statCodesResponse = MatchFunctionGrpc.newBlockingStub(channel).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(header))
                    .getStatCodes(GetStatCodesRequest.newBuilder().build());
        });

    }
}