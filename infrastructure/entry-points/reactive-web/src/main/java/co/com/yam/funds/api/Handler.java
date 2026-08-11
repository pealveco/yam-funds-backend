package co.com.yam.funds.api;

import co.com.yam.funds.api.dto.HealthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class Handler {

    public Mono<ServerResponse> health(ServerRequest serverRequest) {
        return ServerResponse.ok().bodyValue(new HealthResponse("UP"));
    }
}
