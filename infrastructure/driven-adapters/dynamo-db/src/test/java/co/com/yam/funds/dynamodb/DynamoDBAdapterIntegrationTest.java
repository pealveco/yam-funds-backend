package co.com.yam.funds.dynamodb;

import co.com.yam.funds.dynamodb.config.DynamoDBTableNames;
import co.com.yam.funds.model.client.Client;
import co.com.yam.funds.model.client.NotificationPreference;
import co.com.yam.funds.model.exception.DuplicateSubscriptionException;
import co.com.yam.funds.model.exception.InsufficientBalanceException;
import co.com.yam.funds.model.exception.SubscriptionNotFoundException;
import co.com.yam.funds.model.fund.Fund;
import co.com.yam.funds.model.subscription.Subscription;
import co.com.yam.funds.model.transaction.Transaction;
import co.com.yam.funds.model.transaction.TransactionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.reactivecommons.utils.ObjectMapperImp;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class DynamoDBAdapterIntegrationTest {
    private static final String TABLE_SUFFIX = UUID.randomUUID().toString().replace("-", "");
    private static final String CLIENTS_TABLE = "clients-" + TABLE_SUFFIX;
    private static final String FUNDS_TABLE = "funds-" + TABLE_SUFFIX;
    private static final String SUBSCRIPTIONS_TABLE = "subscriptions-" + TABLE_SUFFIX;
    private static final String TRANSACTIONS_TABLE = "transactions-" + TABLE_SUFFIX;
    private static final String CLIENT_ID = "client-001";
    private static final String FUND_ID = "1";
    private static final String FUND_NAME = "FPV_YAM_PACTUAL_RECAUDADORA";

    @Container
    private static final GenericContainer<?> DYNAMODB = new GenericContainer<>(
            DockerImageName.parse("amazon/dynamodb-local:latest"))
            .withExposedPorts(8000)
            .withCommand("-jar DynamoDBLocal.jar -sharedDb -inMemory");

    private static DynamoDbAsyncClient dynamoDbAsyncClient;
    private static ClientDynamoDBAdapter clientAdapter;
    private static FundDynamoDBAdapter fundAdapter;
    private static SubscriptionDynamoDBAdapter subscriptionAdapter;
    private static TransactionDynamoDBAdapter transactionAdapter;

    @BeforeAll
    static void setUpDynamoDB() {
        DynamoDBTableNames tableNames = new DynamoDBTableNames(
                CLIENTS_TABLE,
                FUNDS_TABLE,
                SUBSCRIPTIONS_TABLE,
                TRANSACTIONS_TABLE
        );
        dynamoDbAsyncClient = DynamoDbAsyncClient.builder()
                .endpointOverride(URI.create("http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000)))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
                .region(Region.US_EAST_1)
                .build();
        DynamoDbEnhancedAsyncClient enhancedAsyncClient = DynamoDbEnhancedAsyncClient.builder()
                .dynamoDbClient(dynamoDbAsyncClient)
                .build();

        createTable(CLIENTS_TABLE, "id", null);
        createTable(FUNDS_TABLE, "id", null);
        createTable(SUBSCRIPTIONS_TABLE, "clientId", "fundId");
        createTable(TRANSACTIONS_TABLE, "clientId", "sortKey");

        ObjectMapperImp mapper = new ObjectMapperImp();
        clientAdapter = new ClientDynamoDBAdapter(enhancedAsyncClient, dynamoDbAsyncClient, mapper, tableNames);
        fundAdapter = new FundDynamoDBAdapter(enhancedAsyncClient, mapper, tableNames);
        subscriptionAdapter = new SubscriptionDynamoDBAdapter(enhancedAsyncClient, dynamoDbAsyncClient, tableNames);
        transactionAdapter = new TransactionDynamoDBAdapter(enhancedAsyncClient, tableNames);
    }

    @Test
    void shouldPersistAndReadDomainMappingsThroughDynamoDBLocal() {
        // Arrange
        Client client = client("mapping-client", new BigDecimal("500000"));
        Fund fund = fund("mapping-fund");
        Subscription subscription = subscription(client.getId(), fund.getId(), Instant.parse("2026-08-11T00:00:00Z"));
        Transaction transaction = transaction(
                "00000000-0000-0000-0000-000000000001",
                client.getId(),
                fund.getId(),
                TransactionType.SUBSCRIPTION,
                Instant.parse("2026-08-11T00:00:00Z")
        );

        // Act & Assert
        StepVerifier.create(clientAdapter.save(client).then(eventually(() -> clientAdapter.findById(client.getId()))))
                .assertNext(savedClient -> {
                    assertThat(savedClient.getId()).isEqualTo(client.getId());
                    assertThat(savedClient.getNotificationPreference()).isEqualTo(NotificationPreference.EMAIL);
                    assertThat(savedClient.getBalance()).isEqualByComparingTo("500000");
                    assertThat(savedClient.getVersion()).isZero();
                })
                .verifyComplete();

        StepVerifier.create(fundAdapter.save(fund).then(eventually(() -> fundAdapter.findById(fund.getId()))))
                .assertNext(savedFund -> {
                    assertThat(savedFund.getId()).isEqualTo(fund.getId());
                    assertThat(savedFund.getMinimumAmount()).isEqualByComparingTo("75000");
                    assertThat(savedFund.getCategory()).isEqualTo("FPV");
                })
                .verifyComplete();

        StepVerifier.create(subscriptionAdapter.save(subscription)
                        .then(eventually(() -> subscriptionAdapter.find(
                                subscription.getClientId(),
                                subscription.getFundId()))))
                .assertNext(savedSubscription -> {
                    assertThat(savedSubscription.getClientId()).isEqualTo(subscription.getClientId());
                    assertThat(savedSubscription.getFundId()).isEqualTo(subscription.getFundId());
                    assertThat(savedSubscription.getFundName()).isEqualTo(FUND_NAME);
                    assertThat(savedSubscription.getAmount()).isEqualByComparingTo("75000");
                    assertThat(savedSubscription.getSubscribedAt()).isEqualTo(subscription.getSubscribedAt());
                })
                .verifyComplete();

        StepVerifier.create(transactionAdapter.save(transaction)
                        .thenMany(transactionHistory(client.getId(), 1)))
                .assertNext(savedTransaction -> {
                    assertThat(savedTransaction.getId()).isEqualTo(transaction.getId());
                    assertThat(savedTransaction.getClientId()).isEqualTo(transaction.getClientId());
                    assertThat(savedTransaction.getFundId()).isEqualTo(transaction.getFundId());
                    assertThat(savedTransaction.getType()).isEqualTo(TransactionType.SUBSCRIPTION);
                    assertThat(savedTransaction.getAmount()).isEqualByComparingTo("75000");
                    assertThat(savedTransaction.getTimestamp()).isEqualTo(transaction.getTimestamp());
                })
                .verifyComplete();
    }

    @Test
    void shouldApplySubscriptionConditionalWritesAtomically() {
        // Arrange
        Client client = client("subscribe-client", new BigDecimal("500000"));
        Fund fund = fund(FUND_ID);
        Subscription subscription = subscription(client.getId(), fund.getId(), Instant.parse("2026-08-11T00:00:00Z"));
        Transaction transaction = transaction(
                "00000000-0000-0000-0000-000000000011",
                client.getId(),
                fund.getId(),
                TransactionType.SUBSCRIPTION,
                Instant.parse("2026-08-11T00:00:00Z")
        );

        StepVerifier.create(clientAdapter.save(client))
                .expectNext(client)
                .verifyComplete();

        // Act & Assert
        StepVerifier.create(subscriptionAdapter.subscribe(client, fund, subscription, transaction))
                .expectNext(subscription)
                .verifyComplete();

        StepVerifier.create(clientAdapter.findById(client.getId()))
                .assertNext(savedClient -> {
                    assertThat(savedClient.getBalance()).isEqualByComparingTo("425000");
                    assertThat(savedClient.getVersion()).isEqualTo(1L);
                })
                .verifyComplete();
        StepVerifier.create(subscriptionAdapter.find(client.getId(), fund.getId()))
                .expectNextMatches(savedSubscription -> savedSubscription.getFundName().equals(FUND_NAME)
                        && savedSubscription.getAmount().compareTo(new BigDecimal("75000")) == 0)
                .verifyComplete();
        StepVerifier.create(transactionAdapter.findByClientId(client.getId()))
                .expectNextMatches(savedTransaction -> savedTransaction.getType() == TransactionType.SUBSCRIPTION)
                .verifyComplete();
    }

    @Test
    void shouldRejectSubscriptionWhenBalanceConditionalWriteFails() {
        // Arrange
        Client client = client("low-balance-client", new BigDecimal("1000"));
        Fund fund = fund(FUND_ID);
        Subscription subscription = subscription(client.getId(), fund.getId(), Instant.parse("2026-08-11T00:00:00Z"));
        Transaction transaction = transaction(
                "00000000-0000-0000-0000-000000000021",
                client.getId(),
                fund.getId(),
                TransactionType.SUBSCRIPTION,
                Instant.parse("2026-08-11T00:00:00Z")
        );

        StepVerifier.create(clientAdapter.save(client))
                .expectNext(client)
                .verifyComplete();

        // Act & Assert
        StepVerifier.create(subscriptionAdapter.subscribe(client, fund, subscription, transaction))
                .expectErrorMatches(error -> error instanceof InsufficientBalanceException
                        && error.getMessage().equals(
                        "You do not have any available balance to link to the fund " + FUND_NAME))
                .verify();

        StepVerifier.create(clientAdapter.findById(client.getId()))
                .assertNext(savedClient -> assertThat(savedClient.getBalance()).isEqualByComparingTo("1000"))
                .verifyComplete();
        StepVerifier.create(subscriptionAdapter.find(client.getId(), fund.getId()))
                .verifyComplete();
        StepVerifier.create(transactionAdapter.findByClientId(client.getId()))
                .verifyComplete();
    }

    @Test
    void shouldRejectDuplicateSubscriptionWithoutApplyingPartialBalanceUpdate() {
        // Arrange
        Client client = client("duplicate-client", new BigDecimal("500000"));
        Fund fund = fund(FUND_ID);
        Subscription firstSubscription = subscription(client.getId(), fund.getId(), Instant.parse("2026-08-11T00:00:00Z"));
        Transaction firstTransaction = transaction(
                "00000000-0000-0000-0000-000000000031",
                client.getId(),
                fund.getId(),
                TransactionType.SUBSCRIPTION,
                Instant.parse("2026-08-11T00:00:00Z")
        );
        Transaction duplicatedTransaction = transaction(
                "00000000-0000-0000-0000-000000000032",
                client.getId(),
                fund.getId(),
                TransactionType.SUBSCRIPTION,
                Instant.parse("2026-08-11T00:01:00Z")
        );

        StepVerifier.create(clientAdapter.save(client)
                        .then(subscriptionAdapter.subscribe(client, fund, firstSubscription, firstTransaction)))
                .expectNext(firstSubscription)
                .verifyComplete();

        // Act & Assert
        StepVerifier.create(subscriptionAdapter.subscribe(client, fund, firstSubscription, duplicatedTransaction))
                .expectError(DuplicateSubscriptionException.class)
                .verify();

        StepVerifier.create(eventually(() -> clientAdapter.findById(client.getId())
                .filter(savedClient -> savedClient.getBalance().compareTo(new BigDecimal("425000")) == 0
                        && savedClient.getVersion() == 1L)))
                .assertNext(savedClient -> {
                    assertThat(savedClient.getBalance()).isEqualByComparingTo("425000");
                    assertThat(savedClient.getVersion()).isEqualTo(1L);
                })
                .verifyComplete();
        StepVerifier.create(transactionHistory(client.getId(), 1))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void shouldApplyCancellationConditionalWritesAtomically() {
        // Arrange
        Client client = client("cancel-client", new BigDecimal("500000"));
        Fund fund = fund(FUND_ID);
        Subscription subscription = subscription(client.getId(), fund.getId(), Instant.parse("2026-08-11T00:00:00Z"));
        Transaction subscriptionTransaction = transaction(
                "00000000-0000-0000-0000-000000000041",
                client.getId(),
                fund.getId(),
                TransactionType.SUBSCRIPTION,
                Instant.parse("2026-08-11T00:00:00Z")
        );
        Transaction cancellationTransaction = transaction(
                "00000000-0000-0000-0000-000000000042",
                client.getId(),
                fund.getId(),
                TransactionType.CANCELLATION,
                Instant.parse("2026-08-11T00:02:00Z")
        );

        StepVerifier.create(clientAdapter.save(client)
                        .then(subscriptionAdapter.subscribe(client, fund, subscription, subscriptionTransaction)))
                .expectNext(subscription)
                .verifyComplete();

        // Act & Assert
        StepVerifier.create(subscriptionAdapter.cancel(client, subscription, cancellationTransaction))
                .verifyComplete();

        StepVerifier.create(clientAdapter.findById(client.getId()))
                .assertNext(savedClient -> {
                    assertThat(savedClient.getBalance()).isEqualByComparingTo("500000");
                    assertThat(savedClient.getVersion()).isEqualTo(2L);
                })
                .verifyComplete();
        StepVerifier.create(subscriptionAdapter.find(client.getId(), fund.getId()))
                .verifyComplete();
        StepVerifier.create(transactionAdapter.findByClientId(client.getId()))
                .assertNext(transaction -> assertThat(transaction.getType()).isEqualTo(TransactionType.CANCELLATION))
                .assertNext(transaction -> assertThat(transaction.getType()).isEqualTo(TransactionType.SUBSCRIPTION))
                .verifyComplete();
    }

    @Test
    void shouldRejectCancellationWhenSubscriptionConditionalWriteFails() {
        // Arrange
        Client client = client("missing-subscription-client", new BigDecimal("500000"));
        Subscription subscription = subscription(client.getId(), FUND_ID, Instant.parse("2026-08-11T00:00:00Z"));
        Transaction cancellationTransaction = transaction(
                "00000000-0000-0000-0000-000000000051",
                client.getId(),
                FUND_ID,
                TransactionType.CANCELLATION,
                Instant.parse("2026-08-11T00:02:00Z")
        );

        StepVerifier.create(clientAdapter.save(client))
                .expectNext(client)
                .verifyComplete();

        // Act & Assert
        StepVerifier.create(subscriptionAdapter.cancel(client, subscription, cancellationTransaction))
                .expectError(SubscriptionNotFoundException.class)
                .verify();

        StepVerifier.create(clientAdapter.findById(client.getId()))
                .assertNext(savedClient -> {
                    assertThat(savedClient.getBalance()).isEqualByComparingTo("500000");
                    assertThat(savedClient.getVersion()).isZero();
                })
                .verifyComplete();
        StepVerifier.create(transactionAdapter.findByClientId(client.getId()))
                .verifyComplete();
    }

    @Test
    void shouldQueryTransactionHistoryNewestFirst() {
        // Arrange
        String clientId = "history-client";
        Transaction oldest = transaction(
                "00000000-0000-0000-0000-000000000061",
                clientId,
                FUND_ID,
                TransactionType.SUBSCRIPTION,
                Instant.parse("2026-08-11T00:00:00Z")
        );
        Transaction newest = transaction(
                "00000000-0000-0000-0000-000000000062",
                clientId,
                FUND_ID,
                TransactionType.CANCELLATION,
                Instant.parse("2026-08-11T00:03:00Z")
        );

        StepVerifier.create(transactionAdapter.save(oldest).then(transactionAdapter.save(newest)))
                .expectNextMatches(savedTransaction -> savedTransaction == newest)
                .verifyComplete();

        // Act & Assert
        StepVerifier.create(transactionHistory(clientId, 2))
                .assertNext(transaction -> assertThat(transaction.getId()).isEqualTo(newest.getId()))
                .assertNext(transaction -> assertThat(transaction.getId()).isEqualTo(oldest.getId()))
                .verifyComplete();
    }

    private static <T> Mono<T> eventually(Supplier<Mono<T>> source) {
        return Mono.defer(source)
                .repeatWhenEmpty(repeat -> repeat.delayElements(Duration.ofMillis(100)).take(50));
    }

    private static Flux<Transaction> transactionHistory(String clientId, int expectedCount) {
        return Mono.defer(() -> transactionAdapter.findByClientId(clientId).collectList())
                .filter(transactions -> transactions.size() >= expectedCount)
                .repeatWhenEmpty(repeat -> repeat.delayElements(Duration.ofMillis(100)).take(50))
                .flatMapMany(Flux::fromIterable);
    }

    private static void createTable(String tableName, String partitionKey, String sortKey) {
        CreateTableRequest.Builder request = CreateTableRequest.builder()
                .tableName(tableName)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .keySchema(KeySchemaElement.builder()
                        .attributeName(partitionKey)
                        .keyType(KeyType.HASH)
                        .build())
                .attributeDefinitions(AttributeDefinition.builder()
                        .attributeName(partitionKey)
                        .attributeType(ScalarAttributeType.S)
                        .build());

        if (sortKey != null) {
            request.keySchema(
                    KeySchemaElement.builder()
                            .attributeName(partitionKey)
                            .keyType(KeyType.HASH)
                            .build(),
                    KeySchemaElement.builder()
                            .attributeName(sortKey)
                            .keyType(KeyType.RANGE)
                            .build()
            );
            request.attributeDefinitions(
                    AttributeDefinition.builder()
                            .attributeName(partitionKey)
                            .attributeType(ScalarAttributeType.S)
                            .build(),
                    AttributeDefinition.builder()
                            .attributeName(sortKey)
                            .attributeType(ScalarAttributeType.S)
                            .build()
            );
        }

        dynamoDbAsyncClient.createTable(request.build()).join();
    }

    private static Client client(String id, BigDecimal balance) {
        return Client.builder()
                .id(id)
                .name("Default Client")
                .email("client@example.com")
                .phone("+573000000000")
                .notificationPreference(NotificationPreference.EMAIL)
                .balance(balance)
                .version(0L)
                .build();
    }

    private static Fund fund(String id) {
        return Fund.builder()
                .id(id)
                .name(FUND_NAME)
                .minimumAmount(new BigDecimal("75000"))
                .category("FPV")
                .build();
    }

    private static Subscription subscription(String clientId, String fundId, Instant subscribedAt) {
        return Subscription.builder()
                .clientId(clientId)
                .fundId(fundId)
                .fundName(FUND_NAME)
                .amount(new BigDecimal("75000"))
                .subscribedAt(subscribedAt)
                .build();
    }

    private static Transaction transaction(String id,
                                           String clientId,
                                           String fundId,
                                           TransactionType type,
                                           Instant timestamp) {
        return Transaction.builder()
                .id(UUID.fromString(id))
                .clientId(clientId)
                .fundId(fundId)
                .fundName(FUND_NAME)
                .type(type)
                .amount(new BigDecimal("75000"))
                .timestamp(timestamp)
                .build();
    }
}
