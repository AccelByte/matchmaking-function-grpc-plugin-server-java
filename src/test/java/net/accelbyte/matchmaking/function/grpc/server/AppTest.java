package net.accelbyte.matchmaking.function.grpc.server;

import net.accelbyte.matchmaking.function.grpc.server.config.MockedAppConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
		classes = MockedAppConfiguration.class,
		properties = "spring.main.allow-bean-definition-overriding=true"
)
@ActiveProfiles("test")
class AppTest {

	@Test
	void contextLoads() {

	}

}
