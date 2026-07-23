package dev.fincore.payment.transfer.api.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;

@Data
public class TransferRequest {
    @NotNull
    private UUID fromAccountId;

    @NotNull
    private UUID toAccountId;

    @Positive
    private BigDecimal amount;

    private TransferType transferType;
}
