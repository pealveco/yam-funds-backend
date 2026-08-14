package co.com.yam.funds.api.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "YAM Funds Backend API",
                version = "1.0.0",
                description = "Reactive API for client fund subscriptions, cancellations and transaction history."
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Local environment")
        },
        tags = {
                @Tag(name = "Health", description = "Operational health endpoints"),
                @Tag(name = "Subscriptions", description = "Client fund subscription operations"),
                @Tag(name = "Transactions", description = "Client transaction history operations")
        }
)
public class OpenApiConfig {
}
