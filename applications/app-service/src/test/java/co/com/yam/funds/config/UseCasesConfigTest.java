package co.com.yam.funds.config;

import co.com.yam.funds.model.client.gateways.ClientRepository;
import co.com.yam.funds.model.fund.gateways.FundRepository;
import co.com.yam.funds.model.notification.gateways.NotificationRepository;
import co.com.yam.funds.model.subscription.gateways.SubscriptionRepository;
import co.com.yam.funds.model.transaction.gateways.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class UseCasesConfigTest {

    @Test
    void testUseCaseBeansExist() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            String[] beanNames = context.getBeanDefinitionNames();

            boolean useCaseBeanFound = false;
            for (String beanName : beanNames) {
                if (beanName.endsWith("UseCase")) {
                    useCaseBeanFound = true;
                    break;
                }
            }

            assertTrue(useCaseBeanFound, "No beans ending with 'Use Case' were found");
        }
    }

    @Configuration
    @Import(UseCasesConfig.class)
    static class TestConfig {

        @Bean
        public MyUseCase myUseCase() {
            return new MyUseCase();
        }

        @Bean
        public ClientRepository clientRepository() {
            return mock(ClientRepository.class);
        }

        @Bean
        public FundRepository fundRepository() {
            return mock(FundRepository.class);
        }

        @Bean
        public SubscriptionRepository subscriptionRepository() {
            return mock(SubscriptionRepository.class);
        }

        @Bean
        public NotificationRepository notificationRepository() {
            return mock(NotificationRepository.class);
        }

        @Bean
        public TransactionRepository transactionRepository() {
            return mock(TransactionRepository.class);
        }
    }

    static class MyUseCase {
        public String execute() {
            return "MyUseCase Test";
        }
    }
}
