package dev.fincore.payment.transfer.service;

import dev.fincore.payment.transfer.api.dto.request.TransferRequest;
import dev.fincore.payment.transfer.api.dto.response.TransferResponse;

public interface TransferService {
    TransferResponse transfer(TransferRequest request);

    TransferResponse doTransfer(TransferRequest request);
}
