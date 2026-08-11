package co.com.yam.funds.dynamodb;

import co.com.yam.funds.dynamodb.config.DynamoDBTableNames;
import co.com.yam.funds.dynamodb.entity.TransactionEntity;
import co.com.yam.funds.model.transaction.Transaction;
import co.com.yam.funds.model.transaction.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PagePublisher;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionDynamoDBAdapterTest {
    private static final String CLIENT_ID = "client-001";

    @Mock
    private DynamoDbEnhancedAsyncClient enhancedAsyncClient;
    @Mock
    private DynamoDbAsyncTable<TransactionEntity> table;

    private TransactionDynamoDBAdapter adapter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        DynamoDBTableNames tableNames = new DynamoDBTableNames("clients", "funds", "subscriptions", "transactions");
        when(enhancedAsyncClient.table(eq("transactions"), any(TableSchema.class))).thenReturn(table);
        adapter = new TransactionDynamoDBAdapter(enhancedAsyncClient, tableNames);
    }

    @Test
    void shouldQueryClientTransactionsNewestFirstWithoutScan() {
        TransactionEntity newest = entity("00000000-0000-0000-0000-000000000002", "2026-08-11T02:00:00Z");
        TransactionEntity oldest = entity("00000000-0000-0000-0000-000000000001", "2026-08-11T01:00:00Z");
        when(table.query(any(QueryEnhancedRequest.class)))
                .thenReturn(pagePublisher(newest, oldest));

        StepVerifier.create(adapter.findByClientId(CLIENT_ID))
                .expectNextMatches(transaction -> transaction.getId()
                        .equals(UUID.fromString("00000000-0000-0000-0000-000000000002")))
                .expectNextMatches(transaction -> transaction.getId()
                        .equals(UUID.fromString("00000000-0000-0000-0000-000000000001")))
                .verifyComplete();

        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(table).query(captor.capture());
        assertThat(captor.getValue().scanIndexForward()).isFalse();
    }

    @Test
    void shouldBuildTimestampTransactionIdSortKey() {
        TransactionEntity entity = TransactionDynamoDBAdapter.toEntity(Transaction.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .clientId(CLIENT_ID)
                .fundId("1")
                .fundName("FPV_YAM_PACTUAL_RECAUDADORA")
                .type(TransactionType.SUBSCRIPTION)
                .amount(new BigDecimal("75000"))
                .timestamp(Instant.parse("2026-08-11T01:00:00Z"))
                .build());

        assertThat(entity.getSortKey()).isEqualTo("00000000001786410000000000000#00000000-0000-0000-0000-000000000001");
        assertThat(entity.getId()).isEqualTo("00000000-0000-0000-0000-000000000001");
    }

    private TransactionEntity entity(String id, String timestamp) {
        TransactionEntity entity = new TransactionEntity();
        entity.setClientId(CLIENT_ID);
        entity.setSortKey(TransactionDynamoDBAdapter.sortKey(Transaction.builder()
                .id(UUID.fromString(id))
                .timestamp(Instant.parse(timestamp))
                .build()));
        entity.setId(id);
        entity.setFundId("1");
        entity.setFundName("FPV_YAM_PACTUAL_RECAUDADORA");
        entity.setType(TransactionType.SUBSCRIPTION.name());
        entity.setAmount(new BigDecimal("75000"));
        entity.setTimestamp(timestamp);
        return entity;
    }

    private PagePublisher<TransactionEntity> pagePublisher(TransactionEntity... entities) {
        SdkPublisher<Page<TransactionEntity>> publisher = subscriber ->
                Flux.just(Page.create(Arrays.asList(entities))).subscribe(subscriber);
        return PagePublisher.create(publisher);
    }
}
