package co.com.yam.funds.api;

import co.com.yam.funds.api.exception.GlobalExceptionHandler;
import co.com.yam.funds.model.exception.InsufficientBalanceException;
import co.com.yam.funds.model.exception.SubscriptionNotFoundException;
import co.com.yam.funds.model.subscription.Subscription;
import co.com.yam.funds.usecase.cancelsubscription.CancelSubscriptionUseCase;
import co.com.yam.funds.usecase.subscribetofund.SubscribeToFundUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;

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
                .jsonPath("$.message")
                .isEqualTo("You do not have any available balance to link to the fund FPV_YAM_PACTUAL_RECAUDADORA");
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
                .jsonPath("$.message")
                .isEqualTo("Subscription not found for client client-001 and fund 1");
    }
}
