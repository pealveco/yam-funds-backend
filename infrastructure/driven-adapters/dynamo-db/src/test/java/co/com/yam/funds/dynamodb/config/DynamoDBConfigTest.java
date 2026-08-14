package co.com.yam.funds.dynamodb.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.metrics.MetricPublisher;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class DynamoDBConfigTest {

    @Mock
    private MetricPublisher publisher;

    @Mock
    private DynamoDbAsyncClient dynamoDbAsyncClient;

    private final DynamoDBConfig dynamoDBConfig = new DynamoDBConfig();

    @Test
    void testAmazonDynamoDB() {

        DynamoDbAsyncClient result = dynamoDBConfig.amazonDynamoDB(
                "http://localhost:8000",
                "us-east-1",
                publisher);

        assertNotNull(result);
        result.close();
    }


    @Test
    void testGetDynamoDbEnhancedAsyncClient() {
        var result = dynamoDBConfig.getDynamoDbEnhancedAsyncClient(dynamoDbAsyncClient);

        assertNotNull(result);
    }
}
