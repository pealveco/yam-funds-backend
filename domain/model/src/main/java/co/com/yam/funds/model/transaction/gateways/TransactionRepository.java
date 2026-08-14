package co.com.yam.funds.model.transaction.gateways;

import co.com.yam.funds.model.transaction.Transaction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TransactionRepository {
    Mono<Transaction> save(Transaction transaction);
    Flux<Transaction> findByClientId(String clientId);
}
