package co.com.yam.funds.model.client.gateways;

import co.com.yam.funds.model.client.Client;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface ClientRepository {
    Mono<Client> findById(String id);
    Mono<Client> save(Client client);
    Mono<Client> decreaseBalance(String id, BigDecimal amount);
    Mono<Client> restoreBalance(String id, BigDecimal amount);
}
