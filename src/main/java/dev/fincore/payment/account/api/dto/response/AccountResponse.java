package dev.fincore.payment.account.api.dto.response;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;

@Data
public class AccountResponse {
    private final UUID id;
    private final String number;
    private BigDecimal balance;
}
