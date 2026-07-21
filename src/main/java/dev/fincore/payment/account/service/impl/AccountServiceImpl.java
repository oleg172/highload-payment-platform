package dev.fincore.payment.account.service.impl;

import dev.fincore.payment.account.api.dto.request.CreateAccountRequest;
import dev.fincore.payment.account.api.dto.response.AccountResponse;
import dev.fincore.payment.account.domain.Account;
import dev.fincore.payment.account.domain.repository.AccountRepository;
import dev.fincore.payment.account.service.AccountService;
import dev.fincore.payment.common.exception.OperationNotSupportedException;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;

    @Override
    public AccountResponse createAccount(CreateAccountRequest request) {
        log.info("Try creating account [{}]", request.getNumber());

        if (accountRepository.findByNumber(request.getNumber()).isPresent()) {
            log.error("Account [{}] is also present", request.getNumber());
            throw new OperationNotSupportedException("Account also exist");
        }

        Account account = new Account(request.getNumber());
        accountRepository.save(account);

        return new AccountResponse(account.getId(), account.getNumber());
    }

    @Override
    public AccountResponse getAccount(UUID id) {
        log.info("Getting account by id [{}]", id);

        var accountOpt = accountRepository.findById(id);
        if (accountOpt.isEmpty()) {
            log.warn("Can't find account by id [{}]", id);
            throw new EntityNotFoundException("Account");
        }

        var account = accountOpt.get();

        return new AccountResponse(account.getId(), account.getNumber());
    }
}
