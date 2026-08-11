package co.com.yam.funds.dynamodb;

import co.com.yam.funds.dynamodb.config.DynamoDBTableNames;
import co.com.yam.funds.dynamodb.entity.SubscriptionEntity;
import co.com.yam.funds.model.client.Client;
import co.com.yam.funds.model.exception.DuplicateSubscriptionException;
import co.com.yam.funds.model.exception.InsufficientBalanceException;
import co.com.yam.funds.model.exception.ClientNotFoundException;
import co.com.yam.funds.model.exception.SubscriptionConcurrencyException;
import co.com.yam.funds.model.exception.SubscriptionNotFoundException;
import co.com.yam.funds.model.fund.Fund;
import co.com.yam.funds.model.subscription.Subscription;
import co.com.yam.funds.model.subscription.gateways.SubscriptionRepository;
import co.com.yam.funds.model.transaction.Transaction;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Delete;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Repository
public class SubscriptionDynamoDBAdapter implements SubscriptionRepository {
    private static final String CONDITIONAL_CHECK_FAILED = "ConditionalCheckFailed";

    private final DynamoDbAsyncClient dynamoDbAsyncClient;
    private final DynamoDBTableNames tableNames;
    private final DynamoDbAsyncTable<SubscriptionEntity> table;

    public SubscriptionDynamoDBAdapter(DynamoDbEnhancedAsyncClient enhancedAsyncClient,
                                       DynamoDbAsyncClient dynamoDbAsyncClient,
                                       DynamoDBTableNames tableNames) {
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
        this.tableNames = tableNames;
        this.table = enhancedAsyncClient.table(tableNames.subscriptions(), TableSchema.fromBean(SubscriptionEntity.class));
    }

    @Override
    public Mono<Subscription> find(String clientId, String fundId) {
        return Mono.fromFuture(table.getItem(key(clientId, fundId))).map(SubscriptionDynamoDBAdapter::toModel);
    }

    @Override
    public Mono<Subscription> save(Subscription subscription) {
        return Mono.fromFuture(table.putItem(toEntity(subscription))).thenReturn(subscription);
    }

    @Override
    public Mono<Void> delete(String clientId, String fundId) {
        return Mono.fromFuture(table.deleteItem(key(clientId, fundId))).then();
    }

    @Override
    public Mono<Subscription> subscribe(Client client, Fund fund, Subscription subscription, Transaction transaction) {
        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(List.of(
                        decreaseClientBalance(client.getId(), fund.getMinimumAmount()),
                        putSubscription(subscription),
                        putTransaction(transaction)))
                .build();

        return Mono.fromFuture(dynamoDbAsyncClient.transactWriteItems(request))
                .thenReturn(subscription)
                .onErrorMap(TransactionCanceledException.class,
                        error -> mapTransactionError(error, client.getId(), fund));
    }

    @Override
    public Mono<Void> cancel(Client client, Subscription subscription, Transaction transaction) {
        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(List.of(
                        restoreClientBalance(client.getId(), subscription.getAmount()),
                        deleteSubscription(subscription),
                        putTransaction(transaction)))
                .build();

        return Mono.fromFuture(dynamoDbAsyncClient.transactWriteItems(request))
                .then()
                .onErrorMap(TransactionCanceledException.class,
                        error -> mapCancellationTransactionError(error, client.getId(), subscription.getFundId()));
    }

    private static Key key(String clientId, String fundId) {
        return Key.builder().partitionValue(clientId).sortValue(fundId).build();
    }

    private TransactWriteItem decreaseClientBalance(String clientId, BigDecimal amount) {
        return TransactWriteItem.builder()
                .update(Update.builder()
                        .tableName(tableNames.clients())
                        .key(Map.of("id", text(clientId)))
                        .updateExpression("SET #balance = #balance - :amount, #version = if_not_exists(#version, :zero) + :one")
                        .conditionExpression("attribute_exists(#id) AND #balance >= :amount")
                        .expressionAttributeNames(Map.of(
                                "#id", "id",
                                "#balance", "balance",
                                "#version", "version"))
                        .expressionAttributeValues(Map.of(
                                ":amount", number(amount),
                                ":zero", number(BigDecimal.ZERO),
                                ":one", number(BigDecimal.ONE)))
                        .build())
                .build();
    }

    private TransactWriteItem restoreClientBalance(String clientId, BigDecimal amount) {
        return TransactWriteItem.builder()
                .update(Update.builder()
                        .tableName(tableNames.clients())
                        .key(Map.of("id", text(clientId)))
                        .updateExpression("SET #balance = if_not_exists(#balance, :zero) + :amount, "
                                + "#version = if_not_exists(#version, :zero) + :one")
                        .conditionExpression("attribute_exists(#id)")
                        .expressionAttributeNames(Map.of(
                                "#id", "id",
                                "#balance", "balance",
                                "#version", "version"))
                        .expressionAttributeValues(Map.of(
                                ":amount", number(amount),
                                ":zero", number(BigDecimal.ZERO),
                                ":one", number(BigDecimal.ONE)))
                        .build())
                .build();
    }

    private TransactWriteItem putSubscription(Subscription subscription) {
        return TransactWriteItem.builder()
                .put(Put.builder()
                        .tableName(tableNames.subscriptions())
                        .item(subscriptionItem(subscription))
                        .conditionExpression("attribute_not_exists(#clientId) AND attribute_not_exists(#fundId)")
                        .expressionAttributeNames(Map.of(
                                "#clientId", "clientId",
                                "#fundId", "fundId"))
                        .build())
                .build();
    }

    private TransactWriteItem putTransaction(Transaction transaction) {
        return TransactWriteItem.builder()
                .put(Put.builder()
                        .tableName(tableNames.transactions())
                        .item(transactionItem(transaction))
                        .build())
                .build();
    }

    private TransactWriteItem deleteSubscription(Subscription subscription) {
        return TransactWriteItem.builder()
                .delete(Delete.builder()
                        .tableName(tableNames.subscriptions())
                        .key(Map.of(
                                "clientId", text(subscription.getClientId()),
                                "fundId", text(subscription.getFundId())))
                        .conditionExpression("attribute_exists(#clientId) AND attribute_exists(#fundId)")
                        .expressionAttributeNames(Map.of(
                                "#clientId", "clientId",
                                "#fundId", "fundId"))
                        .build())
                .build();
    }

    private RuntimeException mapTransactionError(TransactionCanceledException error, String clientId, Fund fund) {
        if (isConditionalFailure(error, 1)) {
            return new DuplicateSubscriptionException(clientId, fund.getId());
        }
        if (isConditionalFailure(error, 0)) {
            return InsufficientBalanceException.forFund(fund.getName());
        }
        return new SubscriptionConcurrencyException(clientId, fund.getId());
    }

    private RuntimeException mapCancellationTransactionError(TransactionCanceledException error,
                                                            String clientId,
                                                            String fundId) {
        if (isConditionalFailure(error, 1)) {
            return new SubscriptionNotFoundException(clientId, fundId);
        }
        if (isConditionalFailure(error, 0)) {
            return new ClientNotFoundException(clientId);
        }
        return new SubscriptionConcurrencyException(clientId, fundId);
    }

    private boolean isConditionalFailure(TransactionCanceledException error, int index) {
        return error.cancellationReasons() != null
                && error.cancellationReasons().size() > index
                && CONDITIONAL_CHECK_FAILED.equals(error.cancellationReasons().get(index).code());
    }

    private static Subscription toModel(SubscriptionEntity entity) {
        return Subscription.builder()
                .clientId(entity.getClientId())
                .fundId(entity.getFundId())
                .fundName(entity.getFundName())
                .amount(entity.getAmount())
                .subscribedAt(entity.getSubscribedAt() == null ? null : Instant.parse(entity.getSubscribedAt()))
                .build();
    }

    static SubscriptionEntity toEntity(Subscription subscription) {
        SubscriptionEntity entity = new SubscriptionEntity();
        entity.setClientId(subscription.getClientId());
        entity.setFundId(subscription.getFundId());
        entity.setFundName(subscription.getFundName());
        entity.setAmount(subscription.getAmount());
        entity.setSubscribedAt(subscription.getSubscribedAt() == null ? null : subscription.getSubscribedAt().toString());
        return entity;
    }

    private Map<String, AttributeValue> subscriptionItem(Subscription subscription) {
        return Map.of(
                "clientId", text(subscription.getClientId()),
                "fundId", text(subscription.getFundId()),
                "fundName", text(subscription.getFundName()),
                "amount", number(subscription.getAmount()),
                "subscribedAt", text(subscription.getSubscribedAt().toString())
        );
    }

    private Map<String, AttributeValue> transactionItem(Transaction transaction) {
        return Map.of(
                "clientId", text(transaction.getClientId()),
                "sortKey", text(TransactionDynamoDBAdapter.sortKey(transaction)),
                "id", text(transaction.getId().toString()),
                "fundId", text(transaction.getFundId()),
                "fundName", text(transaction.getFundName()),
                "type", text(transaction.getType().name()),
                "amount", number(transaction.getAmount()),
                "timestamp", text(transaction.getTimestamp().toString())
        );
    }

    private AttributeValue text(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private AttributeValue number(BigDecimal value) {
        return AttributeValue.builder().n(value.toPlainString()).build();
    }
}
