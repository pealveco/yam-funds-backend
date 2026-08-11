package co.com.yam.funds.model.exception;

public class ClientNotFoundException extends RuntimeException {
    public ClientNotFoundException(String clientId) {
        super("Client not found: " + clientId);
    }
}
