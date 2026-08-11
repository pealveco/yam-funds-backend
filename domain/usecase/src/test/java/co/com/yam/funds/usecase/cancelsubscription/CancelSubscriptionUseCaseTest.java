package co.com.yam.funds.usecase.cancelsubscription;

import co.com.yam.funds.model.client.Client;
import co.com.yam.funds.model.client.NotificationPreference;
import co.com.yam.funds.model.client.gateways.ClientRepository;
import co.com.yam.funds.model.exception.ClientNotFoundException;
import co.com.yam.funds.model.exception.SubscriptionNotFoundException;
import co.com.yam.funds.model.subscription.Subscription;
import co.com.yam.funds.model.subscription.gateways.SubscriptionRepository;
import co.com.yam.funds.model.transaction.Transaction;
import co.com.yam.funds.model.transaction.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelSubscriptionUseCaseTest {
    private static final String CLIENT_ID = "client-001";
    private static final String FUND_ID = "1";
    private static final String FUND_NAME = "FPV_YAM_PACTUAL_RECAUDADORA";

    @Mock
    private ClientRepository clientRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;

    private CancelSubscriptionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CancelSubscriptionUseCase(clientRepository, subscriptionRepository);
    }

    @Test
    void shouldCancelSubscription() {
        Client client = client(new BigDecimal("425000"));
        Subscription subscription = subscription();

        when(clientRepository.findById(CLIENT_ID)).thenReturn(Mono.just(client));
        when(subscriptionRepository.find(CLIENT_ID, FUND_ID)).thenReturn(Mono.just(subscription));
        when(subscriptionRepository.cancel(any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(CLIENT_ID, FUND_ID))
                .verifyComplete();

        ArgumentCaptor<Client> clientCaptor = ArgumentCaptor.forClass(Client.class);
        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);

        verify(subscriptionRepository).cancel(clientCaptor.capture(), subscriptionCaptor.capture(), transactionCaptor.capture());
        assertThat(clientCaptor.getValue().getBalance()).isEqualByComparingTo("500000");
        assertThat(subscriptionCaptor.getValue()).isSameAs(subscription);
        assertThat(transactionCaptor.getValue().getId()).isNotNull();
        assertThat(transactionCaptor.getValue().getClientId()).isEqualTo(CLIENT_ID);
        assertThat(transactionCaptor.getValue().getFundId()).isEqualTo(FUND_ID);
        assertThat(transactionCaptor.getValue().getFundName()).isEqualTo(FUND_NAME);
        assertThat(transactionCaptor.getValue().getType()).isEqualTo(TransactionType.CANCELLATION);
        assertThat(transactionCaptor.getValue().getAmount()).isEqualByComparingTo("75000");
        assertThat(transactionCaptor.getValue().getTimestamp()).isNotNull();
    }

    @Test
    void shouldFailWhenSubscriptionDoesNotExist() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Mono.just(client(new BigDecimal("500000"))));
        when(subscriptionRepository.find(CLIENT_ID, FUND_ID)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(CLIENT_ID, FUND_ID))
                .expectError(SubscriptionNotFoundException.class)
                .verify();

        verify(subscriptionRepository, never()).cancel(any(), any(), any());
    }

    @Test
    void shouldFailWhenClientDoesNotExist() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Mono.empty());
        when(subscriptionRepository.find(CLIENT_ID, FUND_ID)).thenReturn(Mono.just(subscription()));

        StepVerifier.create(useCase.execute(CLIENT_ID, FUND_ID))
                .expectError(ClientNotFoundException.class)
                .verify();

        verify(subscriptionRepository, never()).cancel(any(), any(), any());
    }

    private Client client(BigDecimal balance) {
        return Client.builder()
                .id(CLIENT_ID)
                .name("Default Client")
                .email("client@example.com")
                .phone("+573000000000")
                .notificationPreference(NotificationPreference.EMAIL)
                .balance(balance)
                .version(0L)
                .build();
    }

    private Subscription subscription() {
        return Subscription.builder()
                .clientId(CLIENT_ID)
                .fundId(FUND_ID)
                .fundName(FUND_NAME)
                .amount(new BigDecimal("75000"))
                .subscribedAt(Instant.parse("2026-08-11T00:00:00Z"))
                .build();
    }
}
