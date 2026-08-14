package co.com.yam.funds.model.subscription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
//import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
//@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Subscription {
    private String clientId;
    private String fundId;
    private String fundName;
    private BigDecimal amount;
    private Instant subscribedAt;
}
