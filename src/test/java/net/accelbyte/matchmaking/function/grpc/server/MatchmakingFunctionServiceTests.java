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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
public class MatchmakingFunctionServiceTests {

    private MatchmakingFunctionService service;
    
    @Mock
    private StreamObserver<BackfillResponse> backfillResponseObserver;

    @BeforeEach
    void setUp() {
        service = new MatchmakingFunctionService();
    }

    @Test
    void testBackfillMatches_WithParametersAndTickets() throws InterruptedException {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(backfillResponseObserver).onNext(any(BackfillResponse.class));

        // Create test data
        String gameRulesJson = """
            {
                "shipCountMin": 2,
                "shipCountMax": 4,
                "auto_backfill": true,
                "alliance": {
                    "min_number": 1,
                    "max_number": 2,
                    "player_min_number": 2,
                    "player_max_number": 2
                }
            }
            """;

        // Create a backfill ticket
        BackfillTicket.PartialMatch partialMatch = BackfillTicket.PartialMatch.newBuilder()
                .addTickets(createTestTicket("existing-ticket-1", "player1", "player2"))
                .addTeams(BackfillTicket.Team.newBuilder()
                        .addUserIds("player1")
                        .addUserIds("player2")
                        .build())
                .addRegionPreferences("us-east-2")
                .setMatchAttributes(Struct.newBuilder()
                        .putFields("game_mode", Value.newBuilder().setStringValue("battle_royale").build())
                        .build())
                .setBackfill(true)
                .build();

        BackfillTicket backfillTicket = BackfillTicket.newBuilder()
                .setTicketId("backfill-ticket-1")
                .setMatchPool("test-pool")
                .setCreatedAt(Timestamp.newBuilder().setSeconds(System.currentTimeMillis() / 1000).build())
                .setPartialMatch(partialMatch)
                .setMatchSessionId("session-123")
                .build();

        // Create a regular ticket
        Ticket regularTicket = createTestTicket("regular-ticket-1", "player3", "player4");

        // Act
        StreamObserver<BackfillMakeMatchesRequest> requestObserver = 
                service.backfillMatches(backfillResponseObserver);

        // Send parameters first with tickId
        BackfillMakeMatchesRequest parametersRequest = BackfillMakeMatchesRequest.newBuilder()
                .setParameters(BackfillMakeMatchesRequest.MakeMatchesParameters.newBuilder()
                        .setRules(Rules.newBuilder().setJson(gameRulesJson).build())
                        .setTickId(12345L)  // Test tickId
                        .build())
                .build();
        requestObserver.onNext(parametersRequest);

        // Send backfill ticket
        BackfillMakeMatchesRequest backfillRequest = BackfillMakeMatchesRequest.newBuilder()
                .setBackfillTicket(backfillTicket)
                .build();
        requestObserver.onNext(backfillRequest);

        // Send regular ticket
        BackfillMakeMatchesRequest ticketRequest = BackfillMakeMatchesRequest.newBuilder()
                .setTicket(regularTicket)
                .build();
        requestObserver.onNext(ticketRequest);

        requestObserver.onCompleted();

        // Assert
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Backfill response should be received");
        
        ArgumentCaptor<BackfillResponse> responseCaptor = ArgumentCaptor.forClass(BackfillResponse.class);
        verify(backfillResponseObserver, atLeastOnce()).onNext(responseCaptor.capture());
        
        BackfillResponse response = responseCaptor.getValue();
        assertNotNull(response);
        assertNotNull(response.getBackfillProposal());
        
        BackfillProposal proposal = response.getBackfillProposal();
        assertEquals("backfill-ticket-1", proposal.getBackfillTicketId());
        assertEquals("test-pool", proposal.getMatchPool());
        assertEquals("session-123", proposal.getMatchSessionId());
        assertNotNull(proposal.getProposalId());
        assertFalse(proposal.getProposalId().isEmpty());
        
        // Should have added tickets
        assertEquals(1, proposal.getAddedTicketsCount());
        assertEquals("regular-ticket-1", proposal.getAddedTickets(0).getTicketId());
        
        // Should have proposed teams (existing + new)
        assertEquals(2, proposal.getProposedTeamsCount());
        
        // Verify all teams have team IDs
        for (BackfillProposal.Team team : proposal.getProposedTeamsList()) {
            assertNotNull(team.getTeamId(), "Each team should have a team ID");
            assertFalse(team.getTeamId().isEmpty(), "Team ID should not be empty");
        }
        
        // Verify the new team contains the regular ticket players
        boolean foundNewTeam = false;
        for (BackfillProposal.Team team : proposal.getProposedTeamsList()) {
            if (team.getUserIdsList().contains("player3") && team.getUserIdsList().contains("player4")) {
                foundNewTeam = true;
                assertNotNull(team.getTeamId(), "New team should have a team ID");
                break;
            }
        }
        assertTrue(foundNewTeam, "Should have a team with the new players");
    }

    @Test
    void testBackfillMatches_WithOnlyRegularTickets() throws InterruptedException {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        
        // This stubbing won't be used, but we need it for the test structure
        lenient().doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(backfillResponseObserver).onNext(any(BackfillResponse.class));

        // Create test data
        String gameRulesJson = """
            {
                "shipCountMin": 2,
                "shipCountMax": 4,
                "auto_backfill": true,
                "alliance": {
                    "min_number": 1,
                    "max_number": 2,
                    "player_min_number": 2,
                    "player_max_number": 2
                }
            }
            """;

        // Create regular tickets
        Ticket ticket1 = createTestTicket("ticket-1", "player1", "player2");
        Ticket ticket2 = createTestTicket("ticket-2", "player3", "player4");

        // Act
        StreamObserver<BackfillMakeMatchesRequest> requestObserver = 
                service.backfillMatches(backfillResponseObserver);

        // Send parameters with tickId
        BackfillMakeMatchesRequest parametersRequest = BackfillMakeMatchesRequest.newBuilder()
                .setParameters(BackfillMakeMatchesRequest.MakeMatchesParameters.newBuilder()
                        .setRules(Rules.newBuilder().setJson(gameRulesJson).build())
                        .setTickId(67890L)  // Test tickId
                        .build())
                .build();
        requestObserver.onNext(parametersRequest);

        // Send tickets
        requestObserver.onNext(BackfillMakeMatchesRequest.newBuilder().setTicket(ticket1).build());
        requestObserver.onNext(BackfillMakeMatchesRequest.newBuilder().setTicket(ticket2).build());

        requestObserver.onCompleted();

        // Assert - should not create backfill proposals without backfill tickets
        assertFalse(latch.await(2, TimeUnit.SECONDS), "Should not receive backfill response without backfill tickets");
        verify(backfillResponseObserver, never()).onNext(any(BackfillResponse.class));
    }

    @Test
    void testBackfillMatches_ErrorHandling() {
        // Arrange
        // This stubbing won't be used, but we need it for the test structure
        lenient().doThrow(new RuntimeException("Test error")).when(backfillResponseObserver).onNext(any(BackfillResponse.class));

        // Act & Assert
        StreamObserver<BackfillMakeMatchesRequest> requestObserver = 
                service.backfillMatches(backfillResponseObserver);

        // Send invalid request
        BackfillMakeMatchesRequest invalidRequest = BackfillMakeMatchesRequest.newBuilder()
                .setParameters(BackfillMakeMatchesRequest.MakeMatchesParameters.newBuilder()
                        .setRules(Rules.newBuilder().setJson("invalid json").build())
                        .build())
                .build();

        // Should not throw exception
        assertDoesNotThrow(() -> requestObserver.onNext(invalidRequest));
    }

    private Ticket createTestTicket(String ticketId, String player1, String player2) {
        return Ticket.newBuilder()
                .setTicketId(ticketId)
                .setMatchPool("test-pool")
                .setCreatedAt(Timestamp.newBuilder().setSeconds(System.currentTimeMillis() / 1000).build())
                .addPlayers(Ticket.PlayerData.newBuilder()
                        .setPlayerId(player1)
                        .setAttributes(Struct.newBuilder()
                                .putFields("skill", Value.newBuilder().setNumberValue(100).build())
                                .build())
                        .build())
                .addPlayers(Ticket.PlayerData.newBuilder()
                        .setPlayerId(player2)
                        .setAttributes(Struct.newBuilder()
                                .putFields("skill", Value.newBuilder().setNumberValue(120).build())
                                .build())
                        .build())
                .setTicketAttributes(Struct.newBuilder()
                        .putFields("preference", Value.newBuilder().setStringValue("aggressive").build())
                        .build())
                .putLatencies("us-east-2", 50L)
                .setPartySessionId("party-" + ticketId)
                .setNamespace("test-namespace")
                .build();
    }
}