package co.com.yam.funds.api;

import co.com.yam.funds.api.dto.HealthResponse;
import co.com.yam.funds.api.dto.SubscribeToFundRequest;
import co.com.yam.funds.api.dto.SubscriptionResponse;
import co.com.yam.funds.api.dto.TransactionResponse;
import co.com.yam.funds.usecase.cancelsubscription.CancelSubscriptionUseCase;
import co.com.yam.funds.usecase.gettransactionhistory.GetTransactionHistoryUseCase;
import co.com.yam.funds.usecase.subscribetofund.SubscribeToFundUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.net.URI;

@Component
@RequiredArgsConstructor
public class Handler {
    private final SubscribeToFundUseCase subscribeToFundUseCase;
    private final CancelSubscriptionUseCase cancelSubscriptionUseCase;
    private final GetTransactionHistoryUseCase getTransactionHistoryUseCase;

    public Mono<ServerResponse> health(ServerRequest serverRequest) {
        return ServerResponse.ok().bodyValue(new HealthResponse("UP"));
    }

    public Mono<ServerResponse> subscribeToFund(ServerRequest serverRequest) {
        String clientId = serverRequest.pathVariable("clientId");
        return serverRequest.bodyToMono(SubscribeToFundRequest.class)
                .flatMap(request -> subscribeToFundUseCase.execute(clientId, request.fundId()))
                .flatMap(subscription -> ServerResponse
                        .created(URI.create("/api/clients/" + clientId + "/subscriptions/" + subscription.getFundId()))
                        .bodyValue(SubscriptionResponse.from(subscription)));
    }

    public Mono<ServerResponse> cancelSubscription(ServerRequest serverRequest) {
        String clientId = serverRequest.pathVariable("clientId");
        String fundId = serverRequest.pathVariable("fundId");
        return cancelSubscriptionUseCase.execute(clientId, fundId)
                .then(ServerResponse.noContent().build());
    }

    public Mono<ServerResponse> getTransactionHistory(ServerRequest serverRequest) {
        String clientId = serverRequest.pathVariable("clientId");
        return getTransactionHistoryUseCase.execute(clientId)
                .map(TransactionResponse::from)
                .collectList()
                .flatMap(transactions -> ServerResponse.ok().bodyValue(transactions));
    }
}
