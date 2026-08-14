package co.com.yam.funds.model.transaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
//import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
//@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Transaction {
    private UUID id;
    private String clientId;
    private String fundId;
    private String fundName;
    private TransactionType type;
    private BigDecimal amount;
    private Instant timestamp;
}
