package co.com.yam.funds.dynamodb;

import co.com.yam.funds.dynamodb.config.DynamoDBTableNames;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class DynamoDBSeeder implements InitializingBean {
    private static final String DEFAULT_CLIENT_ID = "client-001";

    private final DynamoDbAsyncClient dynamoDbAsyncClient;
    private final DynamoDBTableNames tableNames;
    private final boolean seedEnabled;

    public DynamoDBSeeder(DynamoDbAsyncClient dynamoDbAsyncClient,
                          DynamoDBTableNames tableNames,
                          @Value("${aws.dynamodb.seed-enabled:false}") boolean seedEnabled) {
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
        this.tableNames = tableNames;
        this.seedEnabled = seedEnabled;
    }

    @Override
    public void afterPropertiesSet() {
        if (seedEnabled) {
            createTables()
                    .then(Mono.defer(this::seedFunds))
                    .then(Mono.defer(this::seedClient))
                    .block(Duration.ofSeconds(30));
        }
    }

    private Mono<Void> createTables() {
        return Flux.concat(
                createTableIfMissing(tableNames.clients(), "id", null),
                createTableIfMissing(tableNames.funds(), "id", null),
                createTableIfMissing(tableNames.subscriptions(), "clientId", "fundId"),
                createTableIfMissing(tableNames.transactions(), "clientId", "sortKey")
        ).then();
    }

    private Mono<Void> createTableIfMissing(String tableName, String partitionKey, String sortKey) {
        return Mono.defer(() -> Mono.fromFuture(dynamoDbAsyncClient.describeTable(builder -> builder.tableName(tableName))))
                .then()
                .onErrorResume(this::isResourceNotFound, error -> Mono.defer(() -> Mono.fromFuture(
                        dynamoDbAsyncClient.createTable(createTableRequest(tableName, partitionKey, sortKey))
                )).then())
                .then(waitUntilActive(tableName));
    }

    private Mono<Void> waitUntilActive(String tableName) {
        return Mono.defer(() -> Mono.fromFuture(dynamoDbAsyncClient.describeTable(builder -> builder.tableName(tableName)))
                        .flatMap(response -> TableStatus.ACTIVE.equals(response.table().tableStatus())
                                ? Mono.empty()
                                : Mono.error(new TableNotActiveException(tableName))))
                .retryWhen(Retry.fixedDelay(20, Duration.ofMillis(500))
                        .filter(error -> isResourceNotFound(error)
                                || error instanceof TableNotActiveException))
                .then();
    }

    private boolean isResourceNotFound(Throwable error) {
        return error instanceof ResourceNotFoundException
                || error.getCause() instanceof ResourceNotFoundException;
    }

    private CreateTableRequest createTableRequest(String tableName, String partitionKey, String sortKey) {
        var attributeDefinitions = new java.util.ArrayList<AttributeDefinition>();
        var keySchema = new java.util.ArrayList<KeySchemaElement>();

        attributeDefinitions.add(AttributeDefinition.builder()
                .attributeName(partitionKey)
                .attributeType(ScalarAttributeType.S)
                .build());
        keySchema.add(KeySchemaElement.builder()
                .attributeName(partitionKey)
                .keyType(KeyType.HASH)
                .build());

        if (sortKey != null) {
            attributeDefinitions.add(AttributeDefinition.builder()
                    .attributeName(sortKey)
                    .attributeType(ScalarAttributeType.S)
                    .build());
            keySchema.add(KeySchemaElement.builder()
                    .attributeName(sortKey)
                    .keyType(KeyType.RANGE)
                    .build());
        }

        return CreateTableRequest.builder()
                .tableName(tableName)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(attributeDefinitions)
                .keySchema(keySchema)
                .build();
    }

    private Mono<Void> seedFunds() {
        return Mono.when(funds().stream()
                .map(fund -> putIfAbsent(tableNames.funds(), "id", fund.get("id"), fund))
                .toList());
    }

    private Mono<Void> seedClient() {
        Map<String, AttributeValue> client = Map.of(
                "id", text(DEFAULT_CLIENT_ID),
                "name", text("Default Client"),
                "email", text("client@example.com"),
                "phone", text("+573000000000"),
                "notificationPreference", text("EMAIL"),
                "balance", number(new BigDecimal("500000")),
                "version", number(BigDecimal.ZERO)
        );
        return putIfAbsent(tableNames.clients(), "id", client.get("id"), client);
    }

    private Mono<Void> putIfAbsent(String tableName,
                                   String partitionKey,
                                   AttributeValue partitionValue,
                                   Map<String, AttributeValue> item) {
        PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .conditionExpression("attribute_not_exists(#pk)")
                .expressionAttributeNames(Map.of("#pk", partitionKey))
                .build();

        return Mono.defer(() -> Mono.fromFuture(dynamoDbAsyncClient.putItem(request)))
                .then()
                .onErrorResume(ConditionalCheckFailedException.class, error -> Mono.empty());
    }

    private List<Map<String, AttributeValue>> funds() {
        return List.of(
                fund("1", "FPV_YAM_PACTUAL_RECAUDADORA", "75000", "FPV"),
                fund("2", "FPV_YAM_PACTUAL_ECOPETROL", "125000", "FPV"),
                fund("3", "DEUDAPRIVADA", "50000", "FIC"),
                fund("4", "FDO-ACCIONES", "250000", "FIC"),
                fund("5", "FPV_YAM_PACTUAL_DINAMICA", "100000", "FPV")
        );
    }

    private Map<String, AttributeValue> fund(String id, String name, String minimumAmount, String category) {
        return Map.of(
                "id", text(id),
                "name", text(name),
                "minimumAmount", number(new BigDecimal(minimumAmount)),
                "category", text(category)
        );
    }

    private AttributeValue text(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private AttributeValue number(BigDecimal value) {
        return AttributeValue.builder().n(value.toPlainString()).build();
    }

    private static class TableNotActiveException extends RuntimeException {
        TableNotActiveException(String tableName) {
            super("DynamoDB table is not active yet: " + tableName);
        }
    }
}
