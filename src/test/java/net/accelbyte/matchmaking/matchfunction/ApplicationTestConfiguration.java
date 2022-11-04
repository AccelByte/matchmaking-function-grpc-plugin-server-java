package net.accelbyte.matchmaking.matchfunction;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Profile("test")
@Configuration
public class ApplicationTestConfiguration {

    @Bean
    @Primary
    public ABAuthorizationProvider testAuthorizationProvider() {
        return Mockito.mock(ABAuthorizationProvider.class);
    }
}
