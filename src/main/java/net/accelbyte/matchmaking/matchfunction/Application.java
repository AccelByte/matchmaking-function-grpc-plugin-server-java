package net.accelbyte.matchmaking.matchfunction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {
		"net.accelbyte.matchmaking.matchfunction",
		"net.accelbyte.platform",
		"net.accelbyte.grpc"})
@ConfigurationPropertiesScan(basePackages = {
		"net.accelbyte.grpc"})
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}
