package net.accelbyte.matchmaking.function.grpc.server;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.Timestamp;
import net.accelbyte.matchmakingv2.matchfunction.*;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration test for BackfillMatches functionality.
 * This test demonstrates the complete backfill workflow.
 */
@ExtendWith(MockitoExtension.class)
public class BackfillMatchesIntegrationTest {

    private MatchmakingFunctionService service;
    
    @Mock
    private StreamObserver<BackfillResponse> backfillResponseObserver;

    @BeforeEach
    void setUp() {
        service = new MatchmakingFunctionService();
    }

    @Test
    void testCompleteBackfillWorkflow() throws InterruptedException {
        // Arrange
        CountDownLatch latch = new CountDownLatch(2); // Expect 2 backfill proposals
        
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(backfillResponseObserver).onNext(any(BackfillResponse.class));

        // Create game rules with backfill enabled
        String gameRulesJson = "{\"shipCountMin\":2,\"shipCountMax\":6,\"auto_backfill\":true,\"alliance\":{\"min_number\":1,\"max_number\":3,\"player_min_number\":2,\"player_max_number\":2}}";

        // Create backfill tickets (representing existing matches that need more players)
        BackfillTicket backfillTicket1 = createBackfillTicket("backfill-1", "session-1", 
                createTestTicket("existing-1", "player1", "player2"));
        BackfillTicket backfillTicket2 = createBackfillTicket("backfill-2", "session-2", 
                createTestTicket("existing-2", "player3", "player4"));

        // Create regular tickets (new players looking for matches)
        Ticket newTicket1 = createTestTicket("new-1", "player5", "player6");
        Ticket newTicket2 = createTestTicket("new-2", "player7", "player8");
        Ticket newTicket3 = createTestTicket("new-3", "player9", "player10");

        // Act
        StreamObserver<BackfillMakeMatchesRequest> requestObserver = 
                service.backfillMatches(backfillResponseObserver);

        // Send parameters with tickId
        BackfillMakeMatchesRequest parametersRequest = BackfillMakeMatchesRequest.newBuilder()
                .setParameters(BackfillMakeMatchesRequest.MakeMatchesParameters.newBuilder()
                        .setRules(Rules.newBuilder().setJson(gameRulesJson).build())
                        .setTickId(99999L)  // Test tickId for integration test
                        .build())
                .build();
        requestObserver.onNext(parametersRequest);

        // Send backfill tickets
        requestObserver.onNext(BackfillMakeMatchesRequest.newBuilder()
                .setBackfillTicket(backfillTicket1).build());
        requestObserver.onNext(BackfillMakeMatchesRequest.newBuilder()
                .setBackfillTicket(backfillTicket2).build());

        // Send regular tickets
        requestObserver.onNext(BackfillMakeMatchesRequest.newBuilder()
                .setTicket(newTicket1).build());
        requestObserver.onNext(BackfillMakeMatchesRequest.newBuilder()
                .setTicket(newTicket2).build());
        requestObserver.onNext(BackfillMakeMatchesRequest.newBuilder()
                .setTicket(newTicket3).build());

        requestObserver.onCompleted();

        // Assert
        assertTrue(latch.await(10, TimeUnit.SECONDS), "Should receive 2 backfill proposals");
        
        ArgumentCaptor<BackfillResponse> responseCaptor = ArgumentCaptor.forClass(BackfillResponse.class);
        verify(backfillResponseObserver, times(2)).onNext(responseCaptor.capture());
        
        List<BackfillResponse> responses = responseCaptor.getAllValues();
        
        // Verify first proposal
        BackfillProposal proposal1 = responses.get(0).getBackfillProposal();
        assertNotNull(proposal1);
        assertTrue(proposal1.getBackfillTicketId().equals("backfill-1") || 
                  proposal1.getBackfillTicketId().equals("backfill-2"));
        assertEquals(1, proposal1.getAddedTicketsCount());
        assertEquals(2, proposal1.getProposedTeamsCount());
        
        // Verify all teams in proposal1 have team IDs
        for (BackfillProposal.Team team : proposal1.getProposedTeamsList()) {
            assertNotNull(team.getTeamId(), "Each team should have a team ID");
            assertFalse(team.getTeamId().isEmpty(), "Team ID should not be empty");
        }
        
        // Verify second proposal
        BackfillProposal proposal2 = responses.get(1).getBackfillProposal();
        assertNotNull(proposal2);
        assertTrue(proposal2.getBackfillTicketId().equals("backfill-1") || 
                  proposal2.getBackfillTicketId().equals("backfill-2"));
        assertEquals(1, proposal2.getAddedTicketsCount());
        assertEquals(2, proposal2.getProposedTeamsCount());
        
        // Verify all teams in proposal2 have team IDs
        for (BackfillProposal.Team team : proposal2.getProposedTeamsList()) {
            assertNotNull(team.getTeamId(), "Each team should have a team ID");
            assertFalse(team.getTeamId().isEmpty(), "Team ID should not be empty");
        }
        
        // Verify that the proposals are for different backfill tickets
        assertNotEquals(proposal1.getBackfillTicketId(), proposal2.getBackfillTicketId());
        
        // Verify that all new players are included in the proposals
        List<String> allAddedTicketIds = List.of(
                proposal1.getAddedTickets(0).getTicketId(),
                proposal2.getAddedTickets(0).getTicketId()
        );
        assertTrue(allAddedTicketIds.contains("new-1") || allAddedTicketIds.contains("new-2") || 
                  allAddedTicketIds.contains("new-3"));

        // BackfillMatches Integration Test Passed!
        // Created 2 backfill proposals
        // Matched new players with existing matches
        // All team assignments completed successfully
    }

    private BackfillTicket createBackfillTicket(String ticketId, String sessionId, Ticket existingTicket) {
        BackfillTicket.PartialMatch partialMatch = BackfillTicket.PartialMatch.newBuilder()
                .addTickets(existingTicket)
                .addTeams(BackfillTicket.Team.newBuilder()
                        .addUserIds(existingTicket.getPlayers(0).getPlayerId())
                        .addUserIds(existingTicket.getPlayers(1).getPlayerId())
                        .build())
                .addRegionPreferences("us-east-2")
                .setMatchAttributes(Struct.newBuilder()
                        .putFields("game_mode", Value.newBuilder().setStringValue("battle_royale").build())
                        .putFields("map", Value.newBuilder().setStringValue("desert").build())
                        .build())
                .setBackfill(true)
                .setServerName("game-server-1")
                .setClientVersion("1.0.0")
                .build();

        return BackfillTicket.newBuilder()
                .setTicketId(ticketId)
                .setMatchPool("battle-royale-pool")
                .setCreatedAt(Timestamp.newBuilder().setSeconds(System.currentTimeMillis() / 1000).build())
                .setPartialMatch(partialMatch)
                .setMatchSessionId(sessionId)
                .build();
    }

    private Ticket createTestTicket(String ticketId, String player1, String player2) {
        return Ticket.newBuilder()
                .setTicketId(ticketId)
                .setMatchPool("battle-royale-pool")
                .setCreatedAt(Timestamp.newBuilder().setSeconds(System.currentTimeMillis() / 1000).build())
                .addPlayers(Ticket.PlayerData.newBuilder()
                        .setPlayerId(player1)
                        .setAttributes(Struct.newBuilder()
                                .putFields("skill_level", Value.newBuilder().setNumberValue(85).build())
                                .putFields("preferred_weapon", Value.newBuilder().setStringValue("assault_rifle").build())
                                .build())
                        .build())
                .addPlayers(Ticket.PlayerData.newBuilder()
                        .setPlayerId(player2)
                        .setAttributes(Struct.newBuilder()
                                .putFields("skill_level", Value.newBuilder().setNumberValue(92).build())
                                .putFields("preferred_weapon", Value.newBuilder().setStringValue("sniper").build())
                                .build())
                        .build())
                .setTicketAttributes(Struct.newBuilder()
                        .putFields("game_mode", Value.newBuilder().setStringValue("battle_royale").build())
                        .putFields("team_size", Value.newBuilder().setNumberValue(2).build())
                        .build())
                .putLatencies("us-east-2", 45L)
                .putLatencies("us-west-2", 78L)
                .setPartySessionId("party-" + ticketId)
                .setNamespace("test-namespace")
                .build();
    }
}
