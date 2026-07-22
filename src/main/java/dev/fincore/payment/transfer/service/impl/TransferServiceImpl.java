package dev.fincore.payment.transfer.service.impl;

import dev.fincore.payment.account.domain.Account;
import dev.fincore.payment.account.domain.repository.AccountRepository;
import dev.fincore.payment.common.exception.EntityNotFoundException;
import dev.fincore.payment.common.exception.OperationNotSupportedException;
import dev.fincore.payment.transfer.api.dto.request.TransferRequest;
import dev.fincore.payment.transfer.api.dto.response.TransferResponse;
import dev.fincore.payment.transfer.api.dto.response.TransferStatus;
import dev.fincore.payment.transfer.service.TransferService;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@AllArgsConstructor
public class TransferServiceImpl implements TransferService {

    private final AccountRepository accountRepository;

    @Override
    @Transactional
    public TransferResponse transfer(TransferRequest request) {
        UUID fromId = request.getFromAccountId();
        UUID toId = request.getToAccountId();
        BigDecimal amount = request.getAmount();

        log.info("Processing transfer from [{}] to [{}] amount [{}]", fromId, toId, amount);

        if (fromId.equals(toId)) {
            log.warn("Transfer to the same account [{}] is not allowed", fromId);
            throw new OperationNotSupportedException("Cannot transfer to the same account");
        }

        Account fromAccount = accountRepository.findById(fromId)
                                               .orElseThrow(() -> {
                                                   log.warn("Source account [{}] not found", fromId);
                                                   return new EntityNotFoundException("Account", fromId.toString());
                                               });

        Account toAccount = accountRepository.findById(toId)
                                             .orElseThrow(() -> {
                                                 log.warn("Destination account [{}] not found", toId);
                                                 return new EntityNotFoundException("Account", toId.toString());
                                             });

        fromAccount.withdraw(amount);
        toAccount.deposit(amount);

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        log.info("Transfer completed successfully: from [{}] to [{}], amount [{}]", fromId, toId, amount);

        UUID transferId = UUID.randomUUID();
        return new TransferResponse(transferId, TransferStatus.COMPLETED);
    }
}
