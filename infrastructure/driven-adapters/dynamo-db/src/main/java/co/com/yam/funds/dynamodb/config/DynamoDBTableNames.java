package co.com.yam.funds.dynamodb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DynamoDBTableNames {
    private final String clients;
    private final String funds;
    private final String subscriptions;
    private final String transactions;

    public DynamoDBTableNames(@Value("${aws.dynamodb.tables.clients}") String clients,
                              @Value("${aws.dynamodb.tables.funds}") String funds,
                              @Value("${aws.dynamodb.tables.subscriptions}") String subscriptions,
                              @Value("${aws.dynamodb.tables.transactions}") String transactions) {
        this.clients = clients;
        this.funds = funds;
        this.subscriptions = subscriptions;
        this.transactions = transactions;
    }

    public String clients() {
        return clients;
    }

    public String funds() {
        return funds;
    }

    public String subscriptions() {
        return subscriptions;
    }

    public String transactions() {
        return transactions;
    }
}
