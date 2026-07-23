package dev.fincore.payment.transfer.impl;

import dev.fincore.payment.transfer.TransferControllerIntegrationTest;
import dev.fincore.payment.transfer.api.dto.request.TransferType;

public class OptimisticTransferControllerIntegrationTest extends TransferControllerIntegrationTest {
    @Override
    public TransferType getTransferType() {
        return TransferType.OPTIMISTIC;
    }
}
