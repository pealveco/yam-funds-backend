package co.com.yam.funds.usecase.cancelsubscription;

import co.com.yam.funds.model.client.Client;
import co.com.yam.funds.model.client.gateways.ClientRepository;
import co.com.yam.funds.model.exception.ClientNotFoundException;
import co.com.yam.funds.model.exception.SubscriptionNotFoundException;
import co.com.yam.funds.model.subscription.Subscription;
import co.com.yam.funds.model.subscription.gateways.SubscriptionRepository;
import co.com.yam.funds.model.transaction.Transaction;
import co.com.yam.funds.model.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class CancelSubscriptionUseCase {
    private final ClientRepository clientRepository;
    private final SubscriptionRepository subscriptionRepository;

    public Mono<Void> execute(String clientId, String fundId) {
        Mono<Client> client = clientRepository.findById(clientId)
                .switchIfEmpty(Mono.error(new ClientNotFoundException(clientId)));
        Mono<Subscription> subscription = subscriptionRepository.find(clientId, fundId)
                .switchIfEmpty(Mono.error(new SubscriptionNotFoundException(clientId, fundId)));

        return Mono.zip(client, subscription)
                .flatMap(tuple -> cancel(tuple.getT1(), tuple.getT2()));
    }

    private Mono<Void> cancel(Client client, Subscription subscription) {
        client.restoreBalance(subscription.getAmount());
        Transaction transaction = Transaction.builder()
                .id(UUID.randomUUID())
                .clientId(client.getId())
                .fundId(subscription.getFundId())
                .fundName(subscription.getFundName())
                .type(TransactionType.CANCELLATION)
                .amount(subscription.getAmount())
                .timestamp(Instant.now())
                .build();

        return subscriptionRepository.cancel(client, subscription, transaction);
    }
}
