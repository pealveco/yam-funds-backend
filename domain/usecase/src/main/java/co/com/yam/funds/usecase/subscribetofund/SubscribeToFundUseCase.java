package co.com.yam.funds.usecase.subscribetofund;

import co.com.yam.funds.model.client.Client;
import co.com.yam.funds.model.client.gateways.ClientRepository;
import co.com.yam.funds.model.exception.ClientNotFoundException;
import co.com.yam.funds.model.exception.DuplicateSubscriptionException;
import co.com.yam.funds.model.exception.FundNotFoundException;
import co.com.yam.funds.model.exception.InsufficientBalanceException;
import co.com.yam.funds.model.fund.Fund;
import co.com.yam.funds.model.fund.gateways.FundRepository;
import co.com.yam.funds.model.notification.gateways.NotificationRepository;
import co.com.yam.funds.model.subscription.Subscription;
import co.com.yam.funds.model.subscription.gateways.SubscriptionRepository;
import co.com.yam.funds.model.transaction.Transaction;
import co.com.yam.funds.model.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class SubscribeToFundUseCase {
    private static final System.Logger LOGGER = System.getLogger(SubscribeToFundUseCase.class.getName());

    private final ClientRepository clientRepository;
    private final FundRepository fundRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final NotificationRepository notificationRepository;

    public Mono<Subscription> execute(String clientId, String fundId) {
        Mono<Client> client = clientRepository.findById(clientId)
                .switchIfEmpty(Mono.error(new ClientNotFoundException(clientId)));
        Mono<Fund> fund = fundRepository.findById(fundId)
                .switchIfEmpty(Mono.error(new FundNotFoundException(fundId)));

        return Mono.zip(client, fund)
                .flatMap(tuple -> subscribe(tuple.getT1(), tuple.getT2()));
    }

    private Mono<Subscription> subscribe(Client client, Fund fund) {
        return subscriptionRepository.find(client.getId(), fund.getId())
                .flatMap(subscription -> Mono.<Subscription>error(
                        new DuplicateSubscriptionException(client.getId(), fund.getId())))
                .switchIfEmpty(Mono.defer(() -> persistSubscription(client, fund)));
    }

    private Mono<Subscription> persistSubscription(Client client, Fund fund) {
        if (!client.hasEnoughBalance(fund.getMinimumAmount())) {
            return Mono.error(InsufficientBalanceException.forFund(fund.getName()));
        }

        client.decreaseBalance(fund.getMinimumAmount());
        Subscription subscription = buildSubscription(client, fund);
        Transaction transaction = buildTransaction(client, fund);

        return subscriptionRepository.subscribe(client, fund, subscription, transaction)
                .flatMap(savedSubscription -> notificationRepository.sendNotification(client, fund)
                        .onErrorResume(error -> {
                            LOGGER.log(System.Logger.Level.WARNING,
                                    "Notification failed for client {0} and fund {1}. Financial operation remains committed. Reason: {2}",
                                    client.getId(),
                                    fund.getId(),
                                    error.getMessage());
                            return Mono.empty();
                        })
                        .thenReturn(savedSubscription));
    }

    private Subscription buildSubscription(Client client, Fund fund) {
        return Subscription.builder()
                .clientId(client.getId())
                .fundId(fund.getId())
                .fundName(fund.getName())
                .amount(fund.getMinimumAmount())
                .subscribedAt(Instant.now())
                .build();
    }

    private Transaction buildTransaction(Client client, Fund fund) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .clientId(client.getId())
                .fundId(fund.getId())
                .fundName(fund.getName())
                .type(TransactionType.SUBSCRIPTION)
                .amount(fund.getMinimumAmount())
                .timestamp(Instant.now())
                .build();
    }
}
