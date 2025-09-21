package net.accelbyte.matchmaking.function.grpc.server;

import lombok.Data;

/**
 * AllianceRule represents the alliance configuration for matchmaking.
 */
@Data
public class AllianceRule {
    private int minNumber;
    private int maxNumber;
    private int playerMinNumber;
    private int playerMaxNumber;

    /**
     * Validates the alliance rule configuration.
     * 
     * @return true if the rule is valid, false otherwise
     */
    public boolean isValid() {
        if (minNumber > maxNumber) {
            return false;
        }
        if (playerMinNumber > playerMaxNumber) {
            return false;
        }
        return true;
    }
}