package net.accelbyte.matchmaking.matchfunction.grpc;

import net.accelbyte.platform.security.service.OAuthService;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Profile("test")
@Configuration
public class OAuthServiceTestConfiguration {

    @Bean
    @Primary
    public OAuthService oAuthService() {
        return Mockito.mock(OAuthService.class);
    }

}
