package co.com.yam.funds.model.subscription.gateways;

import co.com.yam.funds.model.subscription.Subscription;
import reactor.core.publisher.Mono;

public interface SubscriptionRepository {
    Mono<Subscription> find(String clientId, String fundId);
    Mono<Subscription> save(Subscription subscription);
    Mono<Void> delete(String clientId, String fundId);
}
