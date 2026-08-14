package co.com.yam.funds.model.fund.gateways;

import co.com.yam.funds.model.fund.Fund;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FundRepository {
    Mono<Fund> findById(String id);
    Flux<Fund> findAll();
}
