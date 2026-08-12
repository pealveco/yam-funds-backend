package co.com.yam.funds.model.client;

import co.com.yam.funds.model.exception.InsufficientBalanceException;
import co.com.yam.funds.model.exception.InvalidAmountException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientTest {
    private static final String CLIENT_ID = "client-001";

    @Test
    void shouldConfirmEnoughBalanceWhenBalanceCoversAmount() {
        // Arrange
        Client client = client(new BigDecimal("100000"));

        // Act
        boolean hasEnoughBalance = client.hasEnoughBalance(new BigDecimal("75000"));

        // Assert
        assertThat(hasEnoughBalance).isTrue();
    }

    @Test
    void shouldRejectEnoughBalanceWhenBalanceIsLowerThanAmount() {
        // Arrange
        Client client = client(new BigDecimal("50000"));

        // Act
        boolean hasEnoughBalance = client.hasEnoughBalance(new BigDecimal("75000"));

        // Assert
        assertThat(hasEnoughBalance).isFalse();
    }

    @Test
    void shouldDecreaseBalanceWhenAmountIsValidAndAvailable() {
        // Arrange
        Client client = client(new BigDecimal("100000"));

        // Act
        client.decreaseBalance(new BigDecimal("75000"));

        // Assert
        assertThat(client.getBalance()).isEqualByComparingTo("25000");
    }

    @Test
    void shouldFailDecreaseWhenBalanceIsInsufficient() {
        // Arrange
        Client client = client(new BigDecimal("50000"));

        // Act & Assert
        assertThatThrownBy(() -> client.decreaseBalance(new BigDecimal("75000")))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessage("Insufficient balance for client " + CLIENT_ID);
        assertThat(client.getBalance()).isEqualByComparingTo("50000");
    }

    @Test
    void shouldRestoreBalanceWhenAmountIsValid() {
        // Arrange
        Client client = client(new BigDecimal("25000"));

        // Act
        client.restoreBalance(new BigDecimal("75000"));

        // Assert
        assertThat(client.getBalance()).isEqualByComparingTo("100000");
    }

    @Test
    void shouldTreatMissingBalanceAsZero() {
        // Arrange
        Client client = client(null);

        // Act
        client.restoreBalance(new BigDecimal("75000"));

        // Assert
        assertThat(client.getBalance()).isEqualByComparingTo("75000");
    }

    @Test
    void shouldRejectInvalidAmounts() {
        // Arrange
        Client client = client(new BigDecimal("100000"));

        // Act & Assert
        assertThatThrownBy(() -> client.hasEnoughBalance(BigDecimal.ZERO))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("Amount must be greater than zero");
        assertThatThrownBy(() -> client.decreaseBalance(null))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("Amount must be greater than zero");
        assertThatThrownBy(() -> client.restoreBalance(new BigDecimal("-1")))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("Amount must be greater than zero");
    }

    private Client client(BigDecimal balance) {
        return Client.builder()
                .id(CLIENT_ID)
                .name("Default Client")
                .email("client@example.com")
                .phone("+573000000000")
                .notificationPreference(NotificationPreference.EMAIL)
                .balance(balance)
                .version(0L)
                .build();
    }
}
