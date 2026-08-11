package co.com.yam.funds.api;

import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class RouterRest {
    @RouterOperations({
            @RouterOperation(path = "/api/health", beanClass = Handler.class, beanMethod = "health"),
            @RouterOperation(path = "/api/clients/{clientId}/subscriptions",
                    beanClass = Handler.class,
                    beanMethod = "subscribeToFund")
    })
    @Bean
    public RouterFunction<ServerResponse> routerFunction(Handler handler) {
        return route(GET("/api/health"), handler::health)
                .andRoute(POST("/api/clients/{clientId}/subscriptions"), handler::subscribeToFund);
    }
}
