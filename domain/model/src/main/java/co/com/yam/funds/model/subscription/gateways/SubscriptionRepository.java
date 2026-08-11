package co.com.yam.funds.model.subscription.gateways;

import co.com.yam.funds.model.client.Client;
import co.com.yam.funds.model.fund.Fund;
import co.com.yam.funds.model.subscription.Subscription;
import co.com.yam.funds.model.transaction.Transaction;
import reactor.core.publisher.Mono;

public interface SubscriptionRepository {
    Mono<Subscription> find(String clientId, String fundId);
    Mono<Subscription> save(Subscription subscription);
    Mono<Void> delete(String clientId, String fundId);
    Mono<Subscription> subscribe(Client client, Fund fund, Subscription subscription, Transaction transaction);
}
