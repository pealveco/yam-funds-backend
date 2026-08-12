package co.com.yam.funds.model.exception;

public class SubscriptionConcurrencyException extends ConcurrencyConflictException {
    public SubscriptionConcurrencyException(String clientId, String fundId) {
        super("Concurrent subscription conflict for client " + clientId + " and fund " + fundId);
    }
}
