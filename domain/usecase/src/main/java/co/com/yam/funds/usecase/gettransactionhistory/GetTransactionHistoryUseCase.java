package co.com.yam.funds.usecase.gettransactionhistory;

import co.com.yam.funds.model.client.gateways.ClientRepository;
import co.com.yam.funds.model.exception.ClientNotFoundException;
import co.com.yam.funds.model.transaction.Transaction;
import co.com.yam.funds.model.transaction.gateways.TransactionRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class GetTransactionHistoryUseCase {
    private final ClientRepository clientRepository;
    private final TransactionRepository transactionRepository;

    public Flux<Transaction> execute(String clientId) {
        return clientRepository.findById(clientId)
                .switchIfEmpty(Mono.error(new ClientNotFoundException(clientId)))
                .thenMany(transactionRepository.findByClientId(clientId));
    }
}
