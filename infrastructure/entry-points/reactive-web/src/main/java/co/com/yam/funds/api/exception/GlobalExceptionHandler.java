package co.com.yam.funds.api.exception;

import co.com.yam.funds.api.dto.ErrorResponse;
import co.com.yam.funds.model.exception.ClientNotFoundException;
import co.com.yam.funds.model.exception.ConcurrencyConflictException;
import co.com.yam.funds.model.exception.DuplicateSubscriptionException;
import co.com.yam.funds.model.exception.FundNotFoundException;
import co.com.yam.funds.model.exception.InsufficientBalanceException;
import co.com.yam.funds.model.exception.InvalidAmountException;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
@Order(-2)
public class GlobalExceptionHandler implements WebExceptionHandler {
    private static final System.Logger LOGGER = System.getLogger(GlobalExceptionHandler.class.getName());
    private static final String UNEXPECTED_ERROR = "Unexpected error";

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

        if (status.is5xxServerError()) {
            LOGGER.log(System.Logger.Level.ERROR, "Unhandled application error: {0}", ex.getMessage());
        }

        byte[] body = body(ex, status);
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    private HttpStatus status(Throwable ex) {
        if (ex instanceof ClientNotFoundException
                || ex instanceof FundNotFoundException
                || ex instanceof SubscriptionNotFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        if (ex instanceof DuplicateSubscriptionException || ex instanceof ConcurrencyConflictException) {
            return HttpStatus.CONFLICT;
        }
        if (ex instanceof InsufficientBalanceException || ex instanceof InvalidAmountException) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private byte[] body(Throwable ex, HttpStatus status) {
        try {
            return objectMapper.writeValueAsBytes(new ErrorResponse(errorMessage(ex, status), status.value(), Instant.now()));
        } catch (JsonProcessingException error) {
            return fallbackBody(status).getBytes(StandardCharsets.UTF_8);
        }
    }

    private String errorMessage(Throwable ex, HttpStatus status) {
        if (status.is5xxServerError()) {
            return UNEXPECTED_ERROR;
        }
        return ex.getMessage() == null ? UNEXPECTED_ERROR : ex.getMessage();
    }

    private String fallbackBody(HttpStatus status) {
        return "{\"error\":\"" + UNEXPECTED_ERROR + "\",\"status\":" + status.value()
                + ",\"timestamp\":\"" + Instant.now() + "\"}";
    }
}
