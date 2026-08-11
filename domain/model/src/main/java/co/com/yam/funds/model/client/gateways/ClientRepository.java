package co.com.yam.funds.model.client.gateways;

import co.com.yam.funds.model.client.Client;
import reactor.core.publisher.Mono;

public interface ClientRepository {
    Mono<Client> findById(String id);
    Mono<Client> save(Client client);
}
