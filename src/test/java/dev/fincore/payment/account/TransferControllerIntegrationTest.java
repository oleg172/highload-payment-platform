package dev.fincore.payment.account;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fincore.payment.account.domain.Account;
import dev.fincore.payment.account.domain.repository.AccountRepository;
import dev.fincore.payment.common.exception.OperationNotSupportedException;
import dev.fincore.payment.transfer.api.dto.request.TransferRequest;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
//@Transactional   // откат изменений после каждого теста
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

    @Test
    void concurrentTransfers() throws Exception {
        // Шаг 1: создаём два счета
        Account accountA = accountRepository.save(new Account("A", BigDecimal.valueOf(1_000_000)));
        Account accountB = accountRepository.save(new Account("B", BigDecimal.ZERO));

        final int threads = 100;
        final int transfersPerThread = 1000;
        final BigDecimal amount = BigDecimal.ONE;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Exception> exceptions = new CopyOnWriteArrayList<>();

        // Шаг 2: запускаем потоки
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // ждём сигнала старта
                    for (int j = 0; j < transfersPerThread; j++) {
                        try {
                            TransferRequest request = new TransferRequest();
                            request.setFromAccountId(accountA.getId());
                            request.setToAccountId(accountB.getId());
                            request.setAmount(amount);

                            MvcResult result = mockMvc.perform(post("/api/v1/transfers")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                                    .andReturn();
                            int status = result.getResponse().getStatus();

                            if (status == 200) {
                                successCount.incrementAndGet();
                            } else {
                                System.out.println(status);
                                System.out.println(result.getResponse().getContentAsString());
                                failureCount.incrementAndGet();
                            }
                        } catch (ObjectOptimisticLockingFailureException | OperationNotSupportedException e) {
                            // ожидаемые исключения из-за конкурентности
                            failureCount.incrementAndGet();
                            // можно залогировать, но для стресс-теста просто считаем
                        } catch (Exception e) {
                            failureCount.incrementAndGet();
                            exceptions.add(e); // неожиданные ошибки
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Шаг 3: даём старт всем потокам одновременно
        startLatch.countDown();
        // Шаг 4: ждём завершения всех потоков
        boolean finished = doneLatch.await(10, TimeUnit.MINUTES);
        executor.shutdown();
        assertTrue(finished, "Тест не завершился за 2 минуты");

        // Шаг 5: проверяем инварианты
        Account updatedA = accountRepository.findById(accountA.getId()).orElseThrow();
        Account updatedB = accountRepository.findById(accountB.getId()).orElseThrow();

        // Инвариант №1: общая сумма не изменилась
        BigDecimal total = updatedA.getBalance().add(updatedB.getBalance());
        assertEquals(BigDecimal.valueOf(1_000_000).setScale(2), total,
                "Сумма всех денег изменилась! Инвариант нарушен.");

        // Инвариант №2: балансы не отрицательные
        assertTrue(updatedA.getBalance().signum() >= 0,
                "Баланс счёта A отрицательный: " + updatedA.getBalance());
        assertTrue(updatedB.getBalance().signum() >= 0,
                "Баланс счёта B отрицательный: " + updatedB.getBalance());

        // Выводим статистику
        System.out.println("=== СТАТИСТИКА ===");
        System.out.println("Успешных переводов: " + successCount.get());
        System.out.println("Неудачных переводов: " + failureCount.get());
        System.out.println("Итоговый баланс A: " + updatedA.getBalance());
        System.out.println("Итоговый баланс B: " + updatedB.getBalance());
        System.out.println("Сумма балансов: " + total);

        // Ожидаем, что были ошибки (это и есть цель теста)
        assertTrue(failureCount.get() > 0,
                "Не было ни одной ошибки, что маловероятно при 100 потоках. Вариант 1 не ожидался.");
    }
}
