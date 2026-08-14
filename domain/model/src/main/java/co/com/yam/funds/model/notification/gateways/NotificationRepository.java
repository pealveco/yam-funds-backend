package co.com.yam.funds.model.notification.gateways;

import co.com.yam.funds.model.client.Client;
import co.com.yam.funds.model.fund.Fund;
import reactor.core.publisher.Mono;

public interface NotificationRepository {
    Mono<Void> sendNotification(Client client, Fund fund);
}
