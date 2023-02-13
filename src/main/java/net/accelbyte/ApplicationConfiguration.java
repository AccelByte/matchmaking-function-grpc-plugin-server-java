package net.accelbyte;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.accelbyte.sdk.core.AccelByteConfig;
import net.accelbyte.sdk.core.AccelByteSDK;
import net.accelbyte.sdk.core.client.OkhttpClient;
import net.accelbyte.sdk.core.repository.DefaultConfigRepository;
import net.accelbyte.sdk.core.repository.DefaultTokenRepository;

@Configuration
public class ApplicationConfiguration {
    @Bean
    public AccelByteSDK accelbyteSdk() {
        final AccelByteConfig config = new AccelByteConfig(
                new OkhttpClient(),
                new DefaultTokenRepository(),
                new DefaultConfigRepository());

        return new AccelByteSDK(config);
    }
}
