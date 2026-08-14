package co.com.yam.funds.model.exception;

public class SubscriptionNotFoundException extends RuntimeException {
    public SubscriptionNotFoundException(String clientId, String fundId) {
        super("Subscription not found for client " + clientId + " and fund " + fundId);
    }
}
