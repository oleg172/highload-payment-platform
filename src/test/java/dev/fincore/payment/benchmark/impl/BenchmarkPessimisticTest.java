package dev.fincore.payment.benchmark.impl;

import dev.fincore.payment.benchmark.BenchmarkTest;
import dev.fincore.payment.transfer.api.dto.request.TransferType;

public class BenchmarkPessimisticTest extends BenchmarkTest {

    @Override
    public TransferType getTransferType() {
        return TransferType.PESSIMISTIC;
    }
}