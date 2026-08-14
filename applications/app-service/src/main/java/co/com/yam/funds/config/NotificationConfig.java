package co.com.yam.funds.config;

import co.com.yam.funds.model.notification.gateways.NotificationRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class NotificationConfig {

    @Bean
    @ConditionalOnMissingBean(NotificationRepository.class)
    public NotificationRepository notificationRepository() {
        return (client, fund) -> Mono.empty();
    }
}
