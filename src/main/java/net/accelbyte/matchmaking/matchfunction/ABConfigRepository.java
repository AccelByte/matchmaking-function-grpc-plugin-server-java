package net.accelbyte.matchmaking.matchfunction;

import net.accelbyte.sdk.core.repository.DefaultConfigRepository;

public class ABConfigRepository extends DefaultConfigRepository {
    @Override
    public String getClientId() {
        final String value = System.getenv("APP_SECURITY_CLIENT_ID");

        return value != null ? value : "";
    }

    @Override
    public String getClientSecret() {
        final String value = System.getenv("APP_SECURITY_CLIENT_SECRET");

        return value != null ? value : "";
    }

    @Override
    public String getBaseURL() {
        final String value = System.getenv("APP_BASE_URL");

        return value != null ? value : "https://demo.accelbyte.io"; // Defaults to demo environment base URL if not set
    }
}
