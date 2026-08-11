package co.com.yam.funds.dynamodb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.metrics.MetricPublisher;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

import java.net.URI;
import java.util.Optional;

@Configuration
public class DynamoDBConfig {

    @Bean
    public DynamoDbAsyncClient amazonDynamoDB(@Value("${aws.dynamodb.endpoint:}") String endpoint,
                                              @Value("${aws.region:us-east-1}") String region,
                                              MetricPublisher publisher) {
        var builder = DynamoDbAsyncClient.builder()
                .credentialsProvider(credentialsProvider(endpoint))
                .region(Region.of(region))
                .overrideConfiguration(o -> o.addMetricPublisher(publisher));

        Optional.ofNullable(endpoint)
                .filter(value -> !value.isBlank())
                .map(URI::create)
                .ifPresent(builder::endpointOverride);

        return builder.build();
    }

    @Bean
    public DynamoDbEnhancedAsyncClient getDynamoDbEnhancedAsyncClient(DynamoDbAsyncClient client) {
        return DynamoDbEnhancedAsyncClient.builder()
                .dynamoDbClient(client)
                .build();
    }

    private software.amazon.awssdk.auth.credentials.AwsCredentialsProvider credentialsProvider(String endpoint) {
        if (endpoint != null && !endpoint.isBlank()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local"));
        }
        return DefaultCredentialsProvider.create();
    }

}
