package co.com.yam.funds.dynamodb;

import co.com.yam.funds.dynamodb.config.DynamoDBTableNames;
import co.com.yam.funds.dynamodb.entity.FundEntity;
import co.com.yam.funds.dynamodb.helper.TemplateAdapterOperations;
import co.com.yam.funds.model.fund.Fund;
import co.com.yam.funds.model.fund.gateways.FundRepository;
import org.reactivecommons.utils.ObjectMapper;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;

@Repository
public class FundDynamoDBAdapter extends TemplateAdapterOperations<Fund, String, FundEntity>
        implements FundRepository {

    public FundDynamoDBAdapter(DynamoDbEnhancedAsyncClient enhancedAsyncClient,
                               ObjectMapper mapper,
                               DynamoDBTableNames tableNames) {
        super(enhancedAsyncClient, mapper, FundDynamoDBAdapter::mapToModel, tableNames.funds());
    }

    @Override
    public Mono<Fund> findById(String id) {
        return getById(id);
    }

    @Override
    public Flux<Fund> findAll() {
        return Flux.from(table.scan().items()).map(FundDynamoDBAdapter::mapToModel);
    }

    private static Fund mapToModel(FundEntity entity) {
        return Fund.builder()
                .id(entity.getId())
                .name(entity.getName())
                .minimumAmount(entity.getMinimumAmount())
                .category(entity.getCategory())
                .build();
    }

    static FundEntity mapToEntity(Fund fund) {
        FundEntity entity = new FundEntity();
        entity.setId(fund.getId());
        entity.setName(fund.getName());
        entity.setMinimumAmount(fund.getMinimumAmount());
        entity.setCategory(fund.getCategory());
        return entity;
    }
}
