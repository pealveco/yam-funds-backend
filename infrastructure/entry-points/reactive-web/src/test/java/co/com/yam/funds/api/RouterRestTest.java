package co.com.yam.funds.api;

import co.com.yam.funds.api.exception.GlobalExceptionHandler;
import co.com.yam.funds.model.exception.ClientNotFoundException;
import co.com.yam.funds.model.exception.DuplicateSubscriptionException;
import co.com.yam.funds.model.exception.FundNotFoundException;
import co.com.yam.funds.model.exception.InsufficientBalanceException;
import co.com.yam.funds.model.exception.SubscriptionConcurrencyException;
import co.com.yam.funds.model.exception.SubscriptionNotFoundException;
import co.com.yam.funds.model.subscription.Subscription;
import co.com.yam.funds.model.transaction.Transaction;
import co.com.yam.funds.model.transaction.TransactionType;
import co.com.yam.funds.usecase.cancelsubscription.CancelSubscriptionUseCase;
import co.com.yam.funds.usecase.gettransactionhistory.GetTransactionHistoryUseCase;
import co.com.yam.funds.usecase.subscribetofund.SubscribeToFundUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;

@ContextConfiguration(classes = {RouterRest.class, Handler.class, GlobalExceptionHandler.class})
@WebFluxTest
class RouterRestTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private SubscribeToFundUseCase subscribeToFundUseCase;
    @MockitoBean
    private CancelSubscriptionUseCase cancelSubscriptionUseCase;
    @MockitoBean
    private GetTransactionHistoryUseCase getTransactionHistoryUseCase;

    @Test
    void shouldReturnHealthStatus() {
        webTestClient.get()
                .uri("/api/health")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void shouldSubscribeClientToFund() {
        when(subscribeToFundUseCase.execute("client-001", "1")).thenReturn(Mono.just(Subscription.builder()
                .clientId("client-001")
                .fundId("1")
                .fundName("FPV_YAM_PACTUAL_RECAUDADORA")
                .amount(new BigDecimal("75000"))
                .subscribedAt(Instant.parse("2026-08-11T00:00:00Z"))
                .build()));

        webTestClient.post()
                .uri("/api/clients/client-001/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"fundId\":\"1\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals("Location", "/api/clients/client-001/subscriptions/1")
                .expectBody()
                .jsonPath("$.clientId").isEqualTo("client-001")
                .jsonPath("$.fundId").isEqualTo("1")
                .jsonPath("$.fundName").isEqualTo("FPV_YAM_PACTUAL_RECAUDADORA")
                .jsonPath("$.amount").isEqualTo(75000);
    }

    @Test
    void shouldMapInsufficientBalanceError() {
        when(subscribeToFundUseCase.execute("client-001", "1"))
                .thenReturn(Mono.error(InsufficientBalanceException.forFund("FPV_YAM_PACTUAL_RECAUDADORA")));

        webTestClient.post()
                .uri("/api/clients/client-001/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"fundId\":\"1\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo("You do not have any available balance to link to the fund FPV_YAM_PACTUAL_RECAUDADORA")
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.timestamp").exists()
                .jsonPath("$.trace").doesNotExist();
    }

    @Test
    void shouldMapFundNotFoundError() {
        when(subscribeToFundUseCase.execute("client-001", "not-found"))
                .thenReturn(Mono.error(new FundNotFoundException("not-found")));

        webTestClient.post()
                .uri("/api/clients/client-001/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"fundId\":\"not-found\"}")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Fund not found: not-found")
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.timestamp").exists()
                .jsonPath("$.trace").doesNotExist();
    }

    @Test
    void shouldMapClientNotFoundError() {
        when(getTransactionHistoryUseCase.execute("not-found"))
                .thenReturn(Flux.error(new ClientNotFoundException("not-found")));

        webTestClient.get()
                .uri("/api/clients/not-found/transactions")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Client not found: not-found")
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.timestamp").exists()
                .jsonPath("$.trace").doesNotExist();
    }

    @Test
    void shouldMapDuplicateSubscriptionError() {
        when(subscribeToFundUseCase.execute("client-001", "1"))
                .thenReturn(Mono.error(new DuplicateSubscriptionException("client-001", "1")));

        webTestClient.post()
                .uri("/api/clients/client-001/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"fundId\":\"1\"}")
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.error").isEqualTo("Client client-001 is already subscribed to fund 1")
                .jsonPath("$.status").isEqualTo(409)
                .jsonPath("$.timestamp").exists()
                .jsonPath("$.trace").doesNotExist();
    }

    @Test
    void shouldMapConcurrencyConflictError() {
        when(subscribeToFundUseCase.execute("client-001", "1"))
                .thenReturn(Mono.error(new SubscriptionConcurrencyException("client-001", "1")));

        webTestClient.post()
                .uri("/api/clients/client-001/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"fundId\":\"1\"}")
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.error").isEqualTo("Concurrent subscription conflict for client client-001 and fund 1")
                .jsonPath("$.status").isEqualTo(409)
                .jsonPath("$.timestamp").exists()
                .jsonPath("$.trace").doesNotExist();
    }

    @Test
    void shouldMapUnexpectedErrorWithoutExposingTechnicalDetails() {
        when(subscribeToFundUseCase.execute("client-001", "1"))
                .thenReturn(Mono.error(new RuntimeException("DynamoDB internal stack details")));

        webTestClient.post()
                .uri("/api/clients/client-001/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"fundId\":\"1\"}")
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Unexpected error")
                .jsonPath("$.status").isEqualTo(500)
                .jsonPath("$.timestamp").exists()
                .jsonPath("$.trace").doesNotExist()
                .jsonPath("$.exception").doesNotExist()
                .jsonPath("$.path").doesNotExist();
    }

    @Test
    void shouldCancelSubscription() {
        when(cancelSubscriptionUseCase.execute("client-001", "1")).thenReturn(Mono.empty());

        webTestClient.delete()
                .uri("/api/clients/client-001/subscriptions/1")
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();
    }

    @Test
    void shouldMapSubscriptionNotFoundError() {
        when(cancelSubscriptionUseCase.execute("client-001", "1"))
                .thenReturn(Mono.error(new SubscriptionNotFoundException("client-001", "1")));

        webTestClient.delete()
                .uri("/api/clients/client-001/subscriptions/1")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Subscription not found for client client-001 and fund 1")
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.timestamp").exists()
                .jsonPath("$.trace").doesNotExist();
    }

    @Test
    void shouldReturnTransactionHistory() {
        when(getTransactionHistoryUseCase.execute("client-001")).thenReturn(Flux.just(Transaction.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .clientId("client-001")
                .fundId("1")
                .fundName("FPV_YAM_PACTUAL_RECAUDADORA")
                .type(TransactionType.SUBSCRIPTION)
                .amount(new BigDecimal("75000"))
                .timestamp(Instant.parse("2026-08-11T00:00:00Z"))
                .build()));

        webTestClient.get()
                .uri("/api/clients/client-001/transactions")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo("00000000-0000-0000-0000-000000000001")
                .jsonPath("$[0].clientId").isEqualTo("client-001")
                .jsonPath("$[0].type").isEqualTo("SUBSCRIPTION")
                .jsonPath("$[0].amount").isEqualTo(75000);
    }

    @Test
    void shouldReturnEmptyTransactionHistory() {
        when(getTransactionHistoryUseCase.execute("client-001")).thenReturn(Flux.empty());

        webTestClient.get()
                .uri("/api/clients/client-001/transactions")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .json("[]");
    }
}
