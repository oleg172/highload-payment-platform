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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service("optimisticTransferService")
public class OptimisticTransferServiceImpl implements TransferService {

    private final AccountRepository accountRepository;

    @Autowired
    @Lazy
    private TransferService self;

    public OptimisticTransferServiceImpl(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public TransferResponse transfer(TransferRequest request) {
        int attempts = 0;
        Exception lastException = null;

        while (attempts < 5) {
            try {
                return self.doTransfer(request);
            } catch (ObjectOptimisticLockingFailureException e) {
                attempts++;
                lastException = e;
                LockSupport.parkNanos(
                        ThreadLocalRandom.current()
                                         .nextLong(100_000, 500_000)
                );
                //log.warn("Optimistic lock conflict, attempt {}/5 for transfer from {} to {}", attempts, request.getFromAccountId(), request.getToAccountId());
            }
        }

        //log.error("Transfer failed after 5 attempts due to optimistic lock conflicts");
        throw new RuntimeException("Transfer failed after retries", lastException);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransferResponse doTransfer(TransferRequest request) {
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

        log.info("Transfer completed successfully: from [{}] to [{}], amount [{}]", fromId, toId, amount);

        UUID transferId = UUID.randomUUID();
        return new TransferResponse(transferId, TransferStatus.COMPLETED);
    }
}
