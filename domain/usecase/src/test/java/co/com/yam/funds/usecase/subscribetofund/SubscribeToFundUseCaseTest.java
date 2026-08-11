package co.com.yam.funds.usecase.subscribetofund;

import co.com.yam.funds.model.client.Client;
import co.com.yam.funds.model.client.NotificationPreference;
import co.com.yam.funds.model.client.gateways.ClientRepository;
import co.com.yam.funds.model.exception.ClientNotFoundException;
import co.com.yam.funds.model.exception.DuplicateSubscriptionException;
import co.com.yam.funds.model.exception.FundNotFoundException;
import co.com.yam.funds.model.exception.InsufficientBalanceException;
import co.com.yam.funds.model.exception.SubscriptionConcurrencyException;
import co.com.yam.funds.model.fund.Fund;
import co.com.yam.funds.model.fund.gateways.FundRepository;
import co.com.yam.funds.model.notification.gateways.NotificationRepository;
import co.com.yam.funds.model.subscription.Subscription;
import co.com.yam.funds.model.subscription.gateways.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscribeToFundUseCaseTest {
    private static final String CLIENT_ID = "client-001";
    private static final String FUND_ID = "1";
    private static final String FUND_NAME = "FPV_YAM_PACTUAL_RECAUDADORA";

    @Mock
    private ClientRepository clientRepository;
    @Mock
    private FundRepository fundRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private NotificationRepository notificationRepository;

    private SubscribeToFundUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SubscribeToFundUseCase(
                clientRepository,
                fundRepository,
                subscriptionRepository,
                notificationRepository
        );
    }

    @Test
    void shouldSubscribeClientToFund() {
        Client client = client(new BigDecimal("500000"));
        Fund fund = fund(new BigDecimal("75000"));

        when(clientRepository.findById(CLIENT_ID)).thenReturn(Mono.just(client));
        when(fundRepository.findById(FUND_ID)).thenReturn(Mono.just(fund));
        when(subscriptionRepository.find(CLIENT_ID, FUND_ID)).thenReturn(Mono.empty());
        when(subscriptionRepository.subscribe(any(), any(), any(), any()))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(2)));
        when(notificationRepository.sendNotification(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(CLIENT_ID, FUND_ID))
                .assertNext(subscription -> {
                    assertThat(subscription.getClientId()).isEqualTo(CLIENT_ID);
                    assertThat(subscription.getFundId()).isEqualTo(FUND_ID);
                    assertThat(subscription.getFundName()).isEqualTo(FUND_NAME);
                    assertThat(subscription.getAmount()).isEqualByComparingTo("75000");
                    assertThat(subscription.getSubscribedAt()).isNotNull();
                })
                .verifyComplete();

        ArgumentCaptor<Client> clientCaptor = ArgumentCaptor.forClass(Client.class);
        verify(subscriptionRepository).subscribe(clientCaptor.capture(), any(), any(), any());
        assertThat(clientCaptor.getValue().getBalance()).isEqualByComparingTo("425000");
        verify(notificationRepository).sendNotification(any(Client.class), any(Fund.class));
    }

    @Test
    void shouldReturnExactErrorWhenBalanceIsInsufficient() {
        Client client = client(new BigDecimal("1000"));
        Fund fund = fund(new BigDecimal("75000"));

        when(clientRepository.findById(CLIENT_ID)).thenReturn(Mono.just(client));
        when(fundRepository.findById(FUND_ID)).thenReturn(Mono.just(fund));
        when(subscriptionRepository.find(CLIENT_ID, FUND_ID)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(CLIENT_ID, FUND_ID))
                .expectErrorMatches(error -> error instanceof InsufficientBalanceException
                        && error.getMessage().equals("You do not have any available balance to link to the fund " + FUND_NAME))
                .verify();

        verify(subscriptionRepository, never()).subscribe(any(), any(), any(), any());
        verify(notificationRepository, never()).sendNotification(any(), any());
    }

    @Test
    void shouldFailWhenFundDoesNotExist() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Mono.just(client(new BigDecimal("500000"))));
        when(fundRepository.findById(FUND_ID)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(CLIENT_ID, FUND_ID))
                .expectError(FundNotFoundException.class)
                .verify();
    }

    @Test
    void shouldFailWhenClientDoesNotExist() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Mono.empty());
        when(fundRepository.findById(FUND_ID)).thenReturn(Mono.just(fund(new BigDecimal("75000"))));

        StepVerifier.create(useCase.execute(CLIENT_ID, FUND_ID))
                .expectError(ClientNotFoundException.class)
                .verify();
    }

    @Test
    void shouldFailWhenSubscriptionAlreadyExists() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Mono.just(client(new BigDecimal("500000"))));
        when(fundRepository.findById(FUND_ID)).thenReturn(Mono.just(fund(new BigDecimal("75000"))));
        when(subscriptionRepository.find(CLIENT_ID, FUND_ID)).thenReturn(Mono.just(Subscription.builder()
                .clientId(CLIENT_ID)
                .fundId(FUND_ID)
                .build()));

        StepVerifier.create(useCase.execute(CLIENT_ID, FUND_ID))
                .expectError(DuplicateSubscriptionException.class)
                .verify();

        verify(subscriptionRepository, never()).subscribe(any(), any(), any(), any());
    }

    @Test
    void shouldFailWhenConditionalWriteHasConcurrencyConflict() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Mono.just(client(new BigDecimal("500000"))));
        when(fundRepository.findById(FUND_ID)).thenReturn(Mono.just(fund(new BigDecimal("75000"))));
        when(subscriptionRepository.find(CLIENT_ID, FUND_ID)).thenReturn(Mono.empty());
        when(subscriptionRepository.subscribe(any(), any(), any(), any()))
                .thenReturn(Mono.error(new SubscriptionConcurrencyException(CLIENT_ID, FUND_ID)));

        StepVerifier.create(useCase.execute(CLIENT_ID, FUND_ID))
                .expectError(SubscriptionConcurrencyException.class)
                .verify();
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

    private Fund fund(BigDecimal minimumAmount) {
        return Fund.builder()
                .id(FUND_ID)
                .name(FUND_NAME)
                .minimumAmount(minimumAmount)
                .category("FPV")
                .build();
    }
}
