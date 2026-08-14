package co.com.yam.funds.notifications;

import co.com.yam.funds.model.client.Client;
import co.com.yam.funds.model.client.NotificationPreference;
import co.com.yam.funds.model.fund.Fund;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

class NotificationAdapterTest {
    private final NotificationAdapter adapter = new NotificationAdapter();

    @Test
    void shouldCompleteEmailNotificationFallback() {
        StepVerifier.create(adapter.sendNotification(client(NotificationPreference.EMAIL), fund()))
                .verifyComplete();
    }

    @Test
    void shouldCompleteSmsNotificationFallback() {
        StepVerifier.create(adapter.sendNotification(client(NotificationPreference.SMS), fund()))
                .verifyComplete();
    }

    @Test
    void shouldCompleteWhenNotificationPreferenceIsMissing() {
        StepVerifier.create(adapter.sendNotification(client(null), fund()))
                .verifyComplete();
    }

    private Client client(NotificationPreference preference) {
        return Client.builder()
                .id("client-001")
                .name("Default Client")
                .email("client@example.com")
                .phone("+573000000000")
                .notificationPreference(preference)
                .balance(new BigDecimal("500000"))
                .version(0L)
                .build();
    }

    private Fund fund() {
        return Fund.builder()
                .id("1")
                .name("FPV_YAM_PACTUAL_RECAUDADORA")
                .minimumAmount(new BigDecimal("75000"))
                .category("FPV")
                .build();
    }
}
