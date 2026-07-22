package dev.fincore.payment.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fincore.payment.account.domain.Account;
import dev.fincore.payment.account.domain.repository.AccountRepository;
import dev.fincore.payment.transfer.api.dto.request.TransferRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@Transactional   // откат изменений после каждого теста
public class TransferControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    private UUID fromAccountId;
    private UUID toAccountId;

    @BeforeEach
    void setUp() {
        Account from = new Account("ACC-FROM-001");
        from.setBalance(BigDecimal.valueOf(1000));
    }

    // ------------------- ТЕСТЫ -------------------

    @Test
    void shouldTransferSuccessfully() throws Exception {
        // Создаём счета с начальными балансами (в реальном тесте создаём через репозиторий)
        // Здесь предполагаем, что счета уже существуют.
        // Для чистоты теста создадим их в setUp или прямо здесь.

        Account from = new Account("ACC-001", BigDecimal.valueOf(1000));
        Account to = new Account("ACC-002", BigDecimal.valueOf(500));
        from = accountRepository.save(from);
        to = accountRepository.save(to);

        TransferRequest request = new TransferRequest();
        request.setFromAccountId(from.getId());
        request.setToAccountId(to.getId());
        request.setAmount(BigDecimal.valueOf(100));

        mockMvc.perform(post("/api/v1/transfers")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("COMPLETED"))
               .andExpect(jsonPath("$.transferId").exists());

        // Проверяем балансы в БД
        Account updatedFrom = accountRepository.findById(from.getId()).orElseThrow();
        Account updatedTo = accountRepository.findById(to.getId()).orElseThrow();
        assert updatedFrom.getBalance().compareTo(BigDecimal.valueOf(900)) == 0;
        assert updatedTo.getBalance().compareTo(BigDecimal.valueOf(600)) == 0;
    }

    @Test
    void shouldReturn409WhenInsufficientFunds() throws Exception {
        Account from = new Account("ACC-003", BigDecimal.valueOf(300));
        Account to = new Account("ACC-004", BigDecimal.valueOf(100));
        from = accountRepository.save(from);
        to = accountRepository.save(to);

        TransferRequest request = new TransferRequest();
        request.setFromAccountId(from.getId());
        request.setToAccountId(to.getId());
        request.setAmount(BigDecimal.valueOf(400)); // больше чем 300

        mockMvc.perform(post("/api/v1/transfers")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isConflict()) // 409
               .andExpect(jsonPath("$.error").value("Insufficient funds"));
    }

    @Test
    void shouldReturn404WhenAccountNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        Account to = new Account("ACC-005", BigDecimal.valueOf(100));
        to = accountRepository.save(to);

        TransferRequest request = new TransferRequest();
        request.setFromAccountId(nonExistentId);
        request.setToAccountId(to.getId());
        request.setAmount(BigDecimal.valueOf(50));

        mockMvc.perform(post("/api/v1/transfers")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isNotFound()); // 404
    }

    @Test
    void shouldReturn400WhenTransferToSelf() throws Exception {
        Account account = new Account("ACC-006", BigDecimal.valueOf(1000));
        account = accountRepository.save(account);

        TransferRequest request = new TransferRequest();
        request.setFromAccountId(account.getId());
        request.setToAccountId(account.getId()); // тот же ID
        request.setAmount(BigDecimal.valueOf(100));

        mockMvc.perform(post("/api/v1/transfers")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.error").value("Cannot transfer to the same account"));
    }

    @Test
    void shouldReturn400WhenAmountIsZeroOrNegative() throws Exception {
        Account from = new Account("ACC-007", BigDecimal.valueOf(1000));
        Account to = new Account("ACC-008", BigDecimal.valueOf(500));
        from = accountRepository.save(from);
        to = accountRepository.save(to);

        // amount = 0
        TransferRequest request = new TransferRequest();
        request.setFromAccountId(from.getId());
        request.setToAccountId(to.getId());
        request.setAmount(BigDecimal.ZERO);

        mockMvc.perform(post("/api/v1/transfers")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.amount").value("must be greater than 0"));

        // amount = -10
        request.setAmount(BigDecimal.valueOf(-10));
        mockMvc.perform(post("/api/v1/transfers")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isBadRequest());
    }
}
