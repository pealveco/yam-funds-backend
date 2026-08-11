package co.com.yam.funds.model.exception;

public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(String clientId) {
        super("Insufficient balance for client " + clientId);
    }
}
