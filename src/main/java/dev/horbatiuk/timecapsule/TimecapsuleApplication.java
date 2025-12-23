package dev.horbatiuk.timecapsule;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(
        info = @Info(
                title = "Time Capsule API - Definition",
                version = "1.0",
				license = @License(
						name = "MIT Licence",
						url = "https://mit-license.org/"
				)
        ),
		servers = {
				@Server(
						url = "http://localhost:8080/",
						description = "Development"
				),
				@Server(
						url = "http://timecapsule-for-me.com/",
						description = "Production"
				)
		}
)
@SecurityScheme(
		name = "bearerAuth",
		type = SecuritySchemeType.HTTP,
		scheme = "bearer",
		bearerFormat = "JWT"
)
@SecurityRequirement(name = "bearerAuth")
public class TimecapsuleApplication {

	public static void main(String[] args) {
		SpringApplication.run(TimecapsuleApplication.class, args);
	}

}
