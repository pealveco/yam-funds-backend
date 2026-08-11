package co.com.yam.funds.model.exception;

public class DuplicateSubscriptionException extends RuntimeException {
    public DuplicateSubscriptionException(String clientId, String fundId) {
        super("Client " + clientId + " is already subscribed to fund " + fundId);
    }
}
