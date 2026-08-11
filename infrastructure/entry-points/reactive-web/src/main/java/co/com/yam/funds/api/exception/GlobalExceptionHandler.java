package co.com.yam.funds.api.exception;

import co.com.yam.funds.api.dto.ErrorResponse;
import co.com.yam.funds.model.exception.ClientNotFoundException;
import co.com.yam.funds.model.exception.DuplicateSubscriptionException;
import co.com.yam.funds.model.exception.FundNotFoundException;
import co.com.yam.funds.model.exception.InsufficientBalanceException;
import co.com.yam.funds.model.exception.InvalidAmountException;
import co.com.yam.funds.model.exception.SubscriptionConcurrencyException;
import co.com.yam.funds.model.exception.SubscriptionNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class GlobalExceptionHandler implements WebExceptionHandler {
    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatus status = status(ex);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] body = body(ex);
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    private HttpStatus status(Throwable ex) {
        if (ex instanceof ClientNotFoundException
                || ex instanceof FundNotFoundException
                || ex instanceof SubscriptionNotFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        if (ex instanceof DuplicateSubscriptionException || ex instanceof SubscriptionConcurrencyException) {
            return HttpStatus.CONFLICT;
        }
        if (ex instanceof InsufficientBalanceException || ex instanceof InvalidAmountException) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private byte[] body(Throwable ex) {
        try {
            return objectMapper.writeValueAsBytes(new ErrorResponse(ex.getMessage()));
        } catch (JsonProcessingException error) {
            return "{\"message\":\"Unexpected error\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
