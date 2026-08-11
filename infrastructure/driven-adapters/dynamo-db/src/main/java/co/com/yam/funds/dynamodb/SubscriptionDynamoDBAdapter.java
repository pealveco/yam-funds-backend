package co.com.yam.funds.dynamodb;

import co.com.yam.funds.dynamodb.config.DynamoDBTableNames;
import co.com.yam.funds.dynamodb.entity.SubscriptionEntity;
import co.com.yam.funds.model.subscription.Subscription;
import co.com.yam.funds.model.subscription.gateways.SubscriptionRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.time.Instant;

@Repository
public class SubscriptionDynamoDBAdapter implements SubscriptionRepository {
    private final DynamoDbAsyncTable<SubscriptionEntity> table;

    public SubscriptionDynamoDBAdapter(DynamoDbEnhancedAsyncClient enhancedAsyncClient,
                                       DynamoDBTableNames tableNames) {
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

    private static Key key(String clientId, String fundId) {
        return Key.builder().partitionValue(clientId).sortValue(fundId).build();
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
}
