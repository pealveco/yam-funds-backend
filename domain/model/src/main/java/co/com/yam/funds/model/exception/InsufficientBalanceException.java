package co.com.yam.funds.model.exception;

public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(String clientId) {
        super("Insufficient balance for client " + clientId);
    }

    public static InsufficientBalanceException forFund(String fundName) {
        return new InsufficientBalanceException("You do not have any available balance to link to the fund " + fundName, true);
    }

    private InsufficientBalanceException(String message, boolean ignored) {
        super(message);
    }
}
