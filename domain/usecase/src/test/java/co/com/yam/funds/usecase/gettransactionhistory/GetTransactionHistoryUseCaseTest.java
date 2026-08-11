package co.com.yam.funds.usecase.gettransactionhistory;

import co.com.yam.funds.model.client.Client;
import co.com.yam.funds.model.client.gateways.ClientRepository;
import co.com.yam.funds.model.exception.ClientNotFoundException;
import co.com.yam.funds.model.transaction.Transaction;
import co.com.yam.funds.model.transaction.TransactionType;
import co.com.yam.funds.model.transaction.gateways.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetTransactionHistoryUseCaseTest {
    private static final String CLIENT_ID = "client-001";

    @Mock
    private ClientRepository clientRepository;
    @Mock
    private TransactionRepository transactionRepository;

    private GetTransactionHistoryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetTransactionHistoryUseCase(clientRepository, transactionRepository);
    }

    @Test
    void shouldReturnTransactionHistory() {
        Transaction transaction = transaction("2026-08-11T00:00:00Z");
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Mono.just(Client.builder().id(CLIENT_ID).build()));
        when(transactionRepository.findByClientId(CLIENT_ID)).thenReturn(Flux.just(transaction));

        StepVerifier.create(useCase.execute(CLIENT_ID))
                .expectNext(transaction)
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyHistory() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Mono.just(Client.builder().id(CLIENT_ID).build()));
        when(transactionRepository.findByClientId(CLIENT_ID)).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(CLIENT_ID))
                .verifyComplete();
    }

    @Test
    void shouldFailWhenClientDoesNotExist() {
        when(clientRepository.findById(CLIENT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(CLIENT_ID))
                .expectError(ClientNotFoundException.class)
                .verify();
    }

    private Transaction transaction(String timestamp) {
        return Transaction.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .clientId(CLIENT_ID)
                .fundId("1")
                .fundName("FPV_YAM_PACTUAL_RECAUDADORA")
                .type(TransactionType.SUBSCRIPTION)
                .amount(new BigDecimal("75000"))
                .timestamp(Instant.parse(timestamp))
                .build();
    }
}
