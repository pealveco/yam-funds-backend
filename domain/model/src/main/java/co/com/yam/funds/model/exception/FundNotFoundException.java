package co.com.yam.funds.model.exception;

public class FundNotFoundException extends RuntimeException {
    public FundNotFoundException(String fundId) {
        super("Fund not found: " + fundId);
    }
}
