package net.accelbyte.matchmaking.function.grpc.server;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RuleObject {
    private int shipCountMin;
    private int shipCountMax;
}
