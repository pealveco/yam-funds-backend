package co.com.yam.funds.model.exception;

public class InvalidAmountException extends RuntimeException {
    public InvalidAmountException() {
        super("Amount must be greater than zero");
    }
}
