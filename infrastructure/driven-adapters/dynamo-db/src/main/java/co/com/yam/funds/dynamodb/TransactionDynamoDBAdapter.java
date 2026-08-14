package co.com.yam.funds.dynamodb;

import co.com.yam.funds.dynamodb.config.DynamoDBTableNames;
import co.com.yam.funds.dynamodb.entity.TransactionEntity;
import co.com.yam.funds.model.transaction.Transaction;
import co.com.yam.funds.model.transaction.TransactionType;
import co.com.yam.funds.model.transaction.gateways.TransactionRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Repository
public class TransactionDynamoDBAdapter implements TransactionRepository {
    private final DynamoDbAsyncTable<TransactionEntity> table;

    public TransactionDynamoDBAdapter(DynamoDbEnhancedAsyncClient enhancedAsyncClient,
                                      DynamoDBTableNames tableNames) {
        this.table = enhancedAsyncClient.table(tableNames.transactions(), TableSchema.fromBean(TransactionEntity.class));
    }

    @Override
    public Mono<Transaction> save(Transaction transaction) {
        return Mono.fromFuture(table.putItem(toEntity(transaction))).thenReturn(transaction);
    }

    @Override
    public Flux<Transaction> findByClientId(String clientId) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(clientId).build()))
                .scanIndexForward(false)
                .build();
        return Flux.from(table.query(request).items()).map(TransactionDynamoDBAdapter::toModel);
    }

    private static Transaction toModel(TransactionEntity entity) {
        return Transaction.builder()
                .id(entity.getId() == null ? null : UUID.fromString(entity.getId()))
                .clientId(entity.getClientId())
                .fundId(entity.getFundId())
                .fundName(entity.getFundName())
                .type(entity.getType() == null ? null : TransactionType.valueOf(entity.getType()))
                .amount(entity.getAmount())
                .timestamp(entity.getTimestamp() == null ? null : Instant.parse(entity.getTimestamp()))
                .build();
    }

    static TransactionEntity toEntity(Transaction transaction) {
        TransactionEntity entity = new TransactionEntity();
        entity.setId(transaction.getId() == null ? null : transaction.getId().toString());
        entity.setSortKey(sortKey(transaction));
        entity.setClientId(transaction.getClientId());
        entity.setFundId(transaction.getFundId());
        entity.setFundName(transaction.getFundName());
        entity.setType(transaction.getType() == null ? null : transaction.getType().name());
        entity.setAmount(transaction.getAmount());
        entity.setTimestamp(transaction.getTimestamp() == null ? null : transaction.getTimestamp().toString());
        return entity;
    }

    static String sortKey(Transaction transaction) {
        Instant timestamp = transaction.getTimestamp();
        return String.format(Locale.ROOT, "%020d%09d#%s",
                timestamp.getEpochSecond(),
                timestamp.getNano(),
                transaction.getId());
    }
}
