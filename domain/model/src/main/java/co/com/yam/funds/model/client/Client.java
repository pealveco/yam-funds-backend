package co.com.yam.funds.model.client;
import co.com.yam.funds.model.exception.InsufficientBalanceException;
import co.com.yam.funds.model.exception.InvalidAmountException;
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
public class Client {
    private String id;
    private String name;
    private String email;
    private String phone;
    private NotificationPreference notificationPreference;
    private BigDecimal balance;
    private Long version;

    public boolean hasEnoughBalance(BigDecimal amount) {
        validateAmount(amount);
        return getCurrentBalance().compareTo(amount) >= 0;
    }

    public void decreaseBalance(BigDecimal amount) {
        validateAmount(amount);
        if (!hasEnoughBalance(amount)) {
            throw new InsufficientBalanceException(id);
        }
        balance = getCurrentBalance().subtract(amount);
    }

    public void restoreBalance(BigDecimal amount) {
        validateAmount(amount);
        balance = getCurrentBalance().add(amount);
    }

    private BigDecimal getCurrentBalance() {
        return balance == null ? BigDecimal.ZERO : balance;
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidAmountException();
        }
    }
}
