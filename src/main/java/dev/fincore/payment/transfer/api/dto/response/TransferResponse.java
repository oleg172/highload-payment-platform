package dev.fincore.payment.transfer.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.UUID;

@Data
@AllArgsConstructor
public class TransferResponse {
    private UUID transferId;
    private TransferStatus status;
}
