package net.accelbyte;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import net.accelbyte.util.ABAuthorizationProvider;

@Profile("test")
@Configuration
public class TestConfiguration {

    @Bean
    @Primary
    public ABAuthorizationProvider testAuthorizationProvider() {
        return Mockito.mock(ABAuthorizationProvider.class);
    }
}
