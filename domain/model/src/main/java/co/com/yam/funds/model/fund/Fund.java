package co.com.yam.funds.model.fund;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
//import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
//@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Fund {
    private String id;
    private String name;
    private BigDecimal minimumAmount;
    private String category;
}
