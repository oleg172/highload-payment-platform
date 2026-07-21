package dev.fincore.payment.account.service;

import dev.fincore.payment.account.api.dto.request.CreateAccountRequest;
import dev.fincore.payment.account.api.dto.response.AccountResponse;
import java.util.UUID;

public interface AccountService {

    AccountResponse createAccount(CreateAccountRequest request);

    AccountResponse getAccount(UUID id);
}
