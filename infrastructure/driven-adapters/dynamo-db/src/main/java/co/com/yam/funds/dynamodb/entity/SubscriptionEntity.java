package co.com.yam.funds.dynamodb.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.math.BigDecimal;

@DynamoDbBean
public class SubscriptionEntity {
    private String clientId;
    private String fundId;
    private String fundName;
    private BigDecimal amount;
    private String subscribedAt;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("clientId")
    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("fundId")
    public String getFundId() {
        return fundId;
    }

    public void setFundId(String fundId) {
        this.fundId = fundId;
    }

    @DynamoDbAttribute("fundName")
    public String getFundName() {
        return fundName;
    }

    public void setFundName(String fundName) {
        this.fundName = fundName;
    }

    @DynamoDbAttribute("amount")
    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    @DynamoDbAttribute("subscribedAt")
    public String getSubscribedAt() {
        return subscribedAt;
    }

    public void setSubscribedAt(String subscribedAt) {
        this.subscribedAt = subscribedAt;
    }
}
