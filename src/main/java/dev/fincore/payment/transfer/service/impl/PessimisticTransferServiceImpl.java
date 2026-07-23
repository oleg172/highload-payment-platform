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
@Service("pessimisticTransferService")
@AllArgsConstructor
public class PessimisticTransferServiceImpl implements TransferService {

    private final AccountRepository accountRepository;

    @Override
    @Transactional
    public TransferResponse transfer(TransferRequest request) {
        UUID fromId = request.getFromAccountId();
        UUID toId = request.getToAccountId();
        BigDecimal amount = request.getAmount();

        log.info("Pessimistic transfer from {} to {} amount {}", fromId, toId, amount);

        // Проверка перевода самому себе
        if (fromId.equals(toId)) {
            throw new OperationNotSupportedException("Cannot transfer to the same account");
        }

        // Упорядочиваем ID для предотвращения deadlock
        UUID firstId, secondId;
        if (fromId.compareTo(toId) < 0) {
            firstId = fromId;
            secondId = toId;
        } else {
            firstId = toId;
            secondId = fromId;
        }

        // Захватываем блокировки в строгом порядке (по ID)
        Account firstAccount = accountRepository.findByIdForUpdate(firstId)
                                                .orElseThrow(() -> new EntityNotFoundException("Account", firstId.toString()));
        Account secondAccount = accountRepository.findByIdForUpdate(secondId)
                                                 .orElseThrow(() -> new EntityNotFoundException("Account", secondId.toString()));

        // Определяем отправителя и получателя среди двух заблокированных счетов
        Account fromAccount, toAccount;
        if (fromId.equals(firstId)) {
            fromAccount = firstAccount;
            toAccount = secondAccount;
        } else {
            fromAccount = secondAccount;
            toAccount = firstAccount;
        }

        // Выполняем списание и зачисление
        fromAccount.withdraw(amount);
        toAccount.deposit(amount);

        log.info("Pessimistic transfer completed: {} -> {}, amount {}", fromId, toId, amount);

        return new TransferResponse(UUID.randomUUID(), TransferStatus.COMPLETED);
    }

    @Override
    public TransferResponse doTransfer(TransferRequest request) {
        return transfer(request);
    }
}
