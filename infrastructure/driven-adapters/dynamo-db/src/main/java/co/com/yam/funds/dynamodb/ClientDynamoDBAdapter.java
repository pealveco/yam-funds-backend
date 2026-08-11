package co.com.yam.funds.dynamodb;

import co.com.yam.funds.dynamodb.config.DynamoDBTableNames;
import co.com.yam.funds.dynamodb.entity.ClientEntity;
import co.com.yam.funds.dynamodb.helper.TemplateAdapterOperations;
import co.com.yam.funds.model.client.Client;
import co.com.yam.funds.model.client.NotificationPreference;
import co.com.yam.funds.model.client.gateways.ClientRepository;
import co.com.yam.funds.model.exception.InsufficientBalanceException;
import org.reactivecommons.utils.ObjectMapper;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.math.BigDecimal;
import java.util.Map;

@Repository
public class ClientDynamoDBAdapter extends TemplateAdapterOperations<Client, String, ClientEntity>
        implements ClientRepository {
    private final DynamoDbAsyncClient dynamoDbAsyncClient;
    private final String tableName;

    public ClientDynamoDBAdapter(DynamoDbEnhancedAsyncClient enhancedAsyncClient,
                                 DynamoDbAsyncClient dynamoDbAsyncClient,
                                 ObjectMapper mapper,
                                 DynamoDBTableNames tableNames) {
        super(enhancedAsyncClient, mapper, ClientDynamoDBAdapter::mapToModel, tableNames.clients());
        this.dynamoDbAsyncClient = dynamoDbAsyncClient;
        this.tableName = tableNames.clients();
    }

    @Override
    public Mono<Client> findById(String id) {
        return getById(id);
    }

    @Override
    public Mono<Client> save(Client client) {
        return Mono.fromFuture(table.putItem(mapToEntity(client))).thenReturn(client);
    }

    @Override
    public Mono<Client> decreaseBalance(String id, BigDecimal amount) {
        return updateBalance(id, "- :amount", "attribute_exists(#id) AND #balance >= :amount", amount)
                .onErrorMap(ConditionalCheckFailedException.class, error -> new InsufficientBalanceException(id));
    }

    @Override
    public Mono<Client> restoreBalance(String id, BigDecimal amount) {
        return updateBalance(id, "+ :amount", "attribute_exists(#id)", amount);
    }

    private Mono<Client> updateBalance(String id, String operation, String conditionExpression, BigDecimal amount) {
        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("id", AttributeValue.builder().s(id).build()))
                .updateExpression("SET #balance = if_not_exists(#balance, :zero) " + operation
                        + ", #version = if_not_exists(#version, :zero) + :one")
                .conditionExpression(conditionExpression)
                .expressionAttributeNames(Map.of(
                        "#id", "id",
                        "#balance", "balance",
                        "#version", "version"))
                .expressionAttributeValues(Map.of(
                        ":amount", AttributeValue.builder().n(amount.toPlainString()).build(),
                        ":zero", AttributeValue.builder().n("0").build(),
                        ":one", AttributeValue.builder().n("1").build()))
                .returnValues(ReturnValue.ALL_NEW)
                .build();

        return Mono.fromFuture(dynamoDbAsyncClient.updateItem(request))
                .map(response -> toModel(response.attributes()));
    }

    private static Client mapToModel(ClientEntity entity) {
        return Client.builder()
                .id(entity.getId())
                .name(entity.getName())
                .email(entity.getEmail())
                .phone(entity.getPhone())
                .notificationPreference(toNotificationPreference(entity.getNotificationPreference()))
                .balance(entity.getBalance())
                .version(entity.getVersion())
                .build();
    }

    static ClientEntity mapToEntity(Client client) {
        ClientEntity entity = new ClientEntity();
        entity.setId(client.getId());
        entity.setName(client.getName());
        entity.setEmail(client.getEmail());
        entity.setPhone(client.getPhone());
        entity.setNotificationPreference(client.getNotificationPreference() == null
                ? null : client.getNotificationPreference().name());
        entity.setBalance(client.getBalance());
        entity.setVersion(client.getVersion());
        return entity;
    }

    private static Client toModel(Map<String, AttributeValue> attributes) {
        return Client.builder()
                .id(getString(attributes, "id"))
                .name(getString(attributes, "name"))
                .email(getString(attributes, "email"))
                .phone(getString(attributes, "phone"))
                .notificationPreference(toNotificationPreference(getString(attributes, "notificationPreference")))
                .balance(getBigDecimal(attributes, "balance"))
                .version(getLong(attributes, "version"))
                .build();
    }

    private static NotificationPreference toNotificationPreference(String value) {
        return value == null ? null : NotificationPreference.valueOf(value);
    }

    private static String getString(Map<String, AttributeValue> attributes, String name) {
        AttributeValue value = attributes.get(name);
        return value == null ? null : value.s();
    }

    private static BigDecimal getBigDecimal(Map<String, AttributeValue> attributes, String name) {
        AttributeValue value = attributes.get(name);
        return value == null ? null : new BigDecimal(value.n());
    }

    private static Long getLong(Map<String, AttributeValue> attributes, String name) {
        AttributeValue value = attributes.get(name);
        return value == null ? null : Long.valueOf(value.n());
    }
}
