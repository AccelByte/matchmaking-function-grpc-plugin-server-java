package net.accelbyte.matchmaking.matchfunction;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfiguration {
    @Bean
    public ABAuthorizationProvider authorizationProvider() {
        return new ABAuthorizationProvider();
    }
}
