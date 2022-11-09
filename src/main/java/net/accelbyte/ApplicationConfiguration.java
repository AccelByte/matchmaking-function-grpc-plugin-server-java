package net.accelbyte;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.accelbyte.util.ABAuthorizationProvider;

@Configuration
public class ApplicationConfiguration {
    @Bean
    public ABAuthorizationProvider authorizationProvider() {
        return new ABAuthorizationProvider();
    }
}
