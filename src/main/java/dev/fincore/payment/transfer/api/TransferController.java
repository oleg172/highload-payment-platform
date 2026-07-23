package dev.fincore.payment.transfer.api;

import dev.fincore.payment.transfer.api.dto.request.TransferRequest;
import dev.fincore.payment.transfer.api.dto.request.TransferType;
import dev.fincore.payment.transfer.api.dto.response.TransferResponse;
import dev.fincore.payment.transfer.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
@Validated
public class TransferController {
    private final TransferService optimisticTransferService;
    private final TransferService pessimisticTransferService;

    public TransferController(@Qualifier("optimisticTransferService") TransferService optimisticTransferService,
            @Qualifier("pessimisticTransferService") TransferService pessimisticTransferService) {
        this.optimisticTransferService = optimisticTransferService;
        this.pessimisticTransferService = pessimisticTransferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest request) {
        TransferResponse response;
        if (request.getTransferType() == TransferType.OPTIMISTIC) {
            response = optimisticTransferService.transfer(request);
        } else {
            response = pessimisticTransferService.transfer(request);
        }
        return ResponseEntity.ok(response);
    }
}
