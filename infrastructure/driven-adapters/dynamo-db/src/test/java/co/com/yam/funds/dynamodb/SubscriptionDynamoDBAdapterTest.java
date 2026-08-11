package co.com.yam.funds.dynamodb;

import co.com.yam.funds.dynamodb.config.DynamoDBTableNames;
import co.com.yam.funds.dynamodb.entity.SubscriptionEntity;
import co.com.yam.funds.model.client.Client;
import co.com.yam.funds.model.client.NotificationPreference;
import co.com.yam.funds.model.exception.DuplicateSubscriptionException;
import co.com.yam.funds.model.exception.InsufficientBalanceException;
import co.com.yam.funds.model.exception.SubscriptionConcurrencyException;
import co.com.yam.funds.model.exception.SubscriptionNotFoundException;
import co.com.yam.funds.model.fund.Fund;
import co.com.yam.funds.model.subscription.Subscription;
import co.com.yam.funds.model.transaction.Transaction;
import co.com.yam.funds.model.transaction.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionDynamoDBAdapterTest {
    private static final String CLIENT_ID = "client-001";
    private static final String FUND_ID = "1";
    private static final String FUND_NAME = "FPV_YAM_PACTUAL_RECAUDADORA";

    @Mock
    private DynamoDbEnhancedAsyncClient enhancedAsyncClient;
    @Mock
    private DynamoDbAsyncClient dynamoDbAsyncClient;
    @Mock
    private DynamoDbAsyncTable<SubscriptionEntity> table;

    private SubscriptionDynamoDBAdapter adapter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        DynamoDBTableNames tableNames = new DynamoDBTableNames("clients", "funds", "subscriptions", "transactions");
        when(enhancedAsyncClient.table(eq("subscriptions"), any(TableSchema.class))).thenReturn(table);
        adapter = new SubscriptionDynamoDBAdapter(enhancedAsyncClient, dynamoDbAsyncClient, tableNames);
    }

    @Test
    void shouldSubscribeWithTransactionalWrite() {
        Subscription subscription = subscription();
        when(dynamoDbAsyncClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(TransactWriteItemsResponse.builder().build()));

        StepVerifier.create(adapter.subscribe(client(), fund(), subscription, transaction()))
                .expectNext(subscription)
                .verifyComplete();

        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbAsyncClient).transactWriteItems(captor.capture());

        TransactWriteItemsRequest request = captor.getValue();
        assertThat(request.transactItems()).hasSize(3);
        assertThat(request.transactItems().get(0).update().tableName()).isEqualTo("clients");
        assertThat(request.transactItems().get(0).update().conditionExpression())
                .isEqualTo("attribute_exists(#id) AND #balance >= :amount");
        assertThat(request.transactItems().get(1).put().tableName()).isEqualTo("subscriptions");
        assertThat(request.transactItems().get(1).put().conditionExpression())
                .isEqualTo("attribute_not_exists(#clientId) AND attribute_not_exists(#fundId)");
        assertThat(request.transactItems().get(2).put().tableName()).isEqualTo("transactions");
    }

    @Test
    void shouldMapBalanceConditionalFailureToInsufficientBalance() {
        when(dynamoDbAsyncClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(failedTransaction(List.of("ConditionalCheckFailed", "None", "None")));

        StepVerifier.create(adapter.subscribe(client(), fund(), subscription(), transaction()))
                .expectErrorMatches(error -> error instanceof InsufficientBalanceException
                        && error.getMessage().equals(
                        "You do not have any available balance to link to the fund " + FUND_NAME))
                .verify();
    }

    @Test
    void shouldMapSubscriptionConditionalFailureToDuplicateSubscription() {
        when(dynamoDbAsyncClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(failedTransaction(List.of("None", "ConditionalCheckFailed", "None")));

        StepVerifier.create(adapter.subscribe(client(), fund(), subscription(), transaction()))
                .expectError(DuplicateSubscriptionException.class)
                .verify();
    }

    @Test
    void shouldMapUnknownTransactionCancellationToConcurrencyConflict() {
        when(dynamoDbAsyncClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(failedTransaction(List.of("None", "None", "None")));

        StepVerifier.create(adapter.subscribe(client(), fund(), subscription(), transaction()))
                .expectError(SubscriptionConcurrencyException.class)
                .verify();
    }

    @Test
    void shouldCancelSubscriptionWithTransactionalWrite() {
        when(dynamoDbAsyncClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(TransactWriteItemsResponse.builder().build()));

        StepVerifier.create(adapter.cancel(client(), subscription(), cancellationTransaction()))
                .verifyComplete();

        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbAsyncClient).transactWriteItems(captor.capture());

        TransactWriteItemsRequest request = captor.getValue();
        assertThat(request.transactItems()).hasSize(3);
        assertThat(request.transactItems().get(0).update().tableName()).isEqualTo("clients");
        assertThat(request.transactItems().get(0).update().conditionExpression()).isEqualTo("attribute_exists(#id)");
        assertThat(request.transactItems().get(1).delete().tableName()).isEqualTo("subscriptions");
        assertThat(request.transactItems().get(1).delete().conditionExpression())
                .isEqualTo("attribute_exists(#clientId) AND attribute_exists(#fundId)");
        assertThat(request.transactItems().get(2).put().tableName()).isEqualTo("transactions");
    }

    @Test
    void shouldMapCancellationSubscriptionConditionalFailureToSubscriptionNotFound() {
        when(dynamoDbAsyncClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(failedTransaction(List.of("None", "ConditionalCheckFailed", "None")));

        StepVerifier.create(adapter.cancel(client(), subscription(), cancellationTransaction()))
                .expectError(SubscriptionNotFoundException.class)
                .verify();
    }

    private CompletableFuture<TransactWriteItemsResponse> failedTransaction(List<String> reasonCodes) {
        CompletableFuture<TransactWriteItemsResponse> future = new CompletableFuture<>();
        future.completeExceptionally(TransactionCanceledException.builder()
                .cancellationReasons(reasonCodes.stream()
                        .map(code -> CancellationReason.builder().code(code).build())
                        .toList())
                .build());
        return future;
    }

    private Client client() {
        return Client.builder()
                .id(CLIENT_ID)
                .name("Default Client")
                .email("client@example.com")
                .phone("+573000000000")
                .notificationPreference(NotificationPreference.EMAIL)
                .balance(new BigDecimal("500000"))
                .version(0L)
                .build();
    }

    private Fund fund() {
        return Fund.builder()
                .id(FUND_ID)
                .name(FUND_NAME)
                .minimumAmount(new BigDecimal("75000"))
                .category("FPV")
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

    private Transaction transaction() {
        return Transaction.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .clientId(CLIENT_ID)
                .fundId(FUND_ID)
                .fundName(FUND_NAME)
                .type(TransactionType.SUBSCRIPTION)
                .amount(new BigDecimal("75000"))
                .timestamp(Instant.parse("2026-08-11T00:00:00Z"))
                .build();
    }

    private Transaction cancellationTransaction() {
        return Transaction.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                .clientId(CLIENT_ID)
                .fundId(FUND_ID)
                .fundName(FUND_NAME)
                .type(TransactionType.CANCELLATION)
                .amount(new BigDecimal("75000"))
                .timestamp(Instant.parse("2026-08-11T00:00:00Z"))
                .build();
    }
}
