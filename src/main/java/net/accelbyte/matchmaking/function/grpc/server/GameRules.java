package net.accelbyte.matchmaking.function.grpc.server;

import lombok.Data;

/**
 * GameRules represents the complete game rules configuration for matchmaking.
 */
@Data
public class GameRules {
    private int shipCountMin;
    private int shipCountMax;
    private boolean autoBackfill;
    private AllianceRule alliance;

    /**
     * Calculates the minimum number of players required for a match.
     * @return the minimum number of players
     */
    public int getMinPlayers() {
        int minPlayers = 0;
        if (alliance != null) {
            minPlayers = alliance.getMinNumber() * alliance.getPlayerMinNumber();
        }

        if (minPlayers == 0) {
            minPlayers = 2; // Default if not set
        }

        if (shipCountMin != 0) {
            minPlayers *= shipCountMin;
        } else {
            minPlayers *= 1; // Default multiplier
        }
        return minPlayers;
    }

    /**
     * Calculates the maximum number of players for a match.
     * @return the maximum number of players
     */
    public int getMaxPlayers() {
        int maxPlayers = 0;
        if (alliance != null) {
            maxPlayers = alliance.getMaxNumber() * alliance.getPlayerMaxNumber();
        }

        if (maxPlayers == 0) {
            maxPlayers = 2; // Default if not set
        }

        if (shipCountMax != 0) {
            maxPlayers *= shipCountMax;
        } else {
            maxPlayers *= 1; // Default multiplier
        }
        return maxPlayers;
    }
}