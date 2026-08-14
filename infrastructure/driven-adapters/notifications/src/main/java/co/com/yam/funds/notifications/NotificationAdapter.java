package co.com.yam.funds.notifications;

import co.com.yam.funds.model.client.Client;
import co.com.yam.funds.model.client.NotificationPreference;
import co.com.yam.funds.model.fund.Fund;
import co.com.yam.funds.model.notification.gateways.NotificationRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class NotificationAdapter implements NotificationRepository {
    private static final System.Logger LOGGER = System.getLogger(NotificationAdapter.class.getName());

    @Override
    public Mono<Void> sendNotification(Client client, Fund fund) {
        // TODO: Replace this PT fallback with real AWS SNS/SES integration during cloud deployment work.
        return Mono.fromRunnable(() -> logNotification(client, fund)).then();
    }

    private void logNotification(Client client, Fund fund) {
        NotificationPreference preference = client.getNotificationPreference();
        if (preference == null) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Notification preference is missing for client {0}. Fund notification was skipped for fund {1}.",
                    client.getId(),
                    fund.getId());
            return;
        }

        LOGGER.log(System.Logger.Level.INFO,
                "Notification fallback logged through {0} for client {1}, destination {2}, fund {3}.",
                preference.name(),
                client.getId(),
                destination(client, preference),
                fund.getName());
    }

    private String destination(Client client, NotificationPreference preference) {
        return switch (preference) {
            case EMAIL -> client.getEmail();
            case SMS -> client.getPhone();
        };
    }
}
