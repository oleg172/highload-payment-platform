package dev.fincore.payment.benchmark;

import dev.fincore.payment.account.domain.repository.AccountRepository;
import dev.fincore.payment.transfer.api.dto.request.TransferType;
import dev.fincore.payment.transfer.service.TransferService;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
public abstract class BenchmarkTest {

    public abstract TransferType getTransferType();

    // Параметры бенчмарка – можно переопределить в наследниках
    protected int getThreads() { return 100; }
    protected int getOperationsPerThread() { return 100; }
    protected int getTotalAccounts() { return 10000; }
    protected int getHotAccounts() { return 100; }
    protected double getHotProbability() { return 0.9; }
    protected BigDecimal getAmount() { return BigDecimal.ONE; }
    protected BigDecimal getTotalBalance() { return BigDecimal.valueOf(1_000_000); }

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
        registry.add("spring.datasource.hikari.transaction-isolation", () -> "TRANSACTION_READ_COMMITTED");
    }

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    @Qualifier("pessimisticTransferService")
    private TransferService pessimisticService;

    @Autowired
    @Qualifier("optimisticTransferService")
    private TransferService optimisticService;

    @Test
    void runBenchmark() throws InterruptedException {
        BenchmarkRunner runner = new BenchmarkRunner(accountRepository);

        BenchmarkResult result = runner.run(
                getTransferType() == TransferType.OPTIMISTIC ? optimisticService : pessimisticService,
                getTransferType().name(),
                getThreads(),
                getOperationsPerThread(),
                getTotalAccounts(),
                getHotAccounts(),
                getHotProbability(),
                getAmount(),
                getTotalBalance()
        );

        // Выводим сравнительную таблицу
        printComparisonTable(result);
    }

    private void printComparisonTable(BenchmarkResult res) {
        System.out.println("\n=== BENCHMARK COMPARISON ===");
        System.out.println("+-------------------+----------+----------+----------+----------+-------------+");
        System.out.println("| Strategy          | Threads  | Success  | Failed   | Deadlocks| TPS        | Avg (ms) | p95 (ms)| p99 (ms)|");
        System.out.println("+-------------------+----------+----------+----------+----------+-------------+");
        printRow(res);
        System.out.println("+-------------------+----------+----------+----------+----------+-------------+");
    }

    private void printRow(BenchmarkResult result) {
        System.out.printf("| %-17s | %8d | %8d | %8d | %8d | %11.2f | %8.2f | %8.2f | %8.2f |%n",
                result.strategy(),
                result.threads(),
                result.success(),
                result.failed(),
                result.deadlocks(),
                result.tps(),
                result.avgLatencyMs(),
                result.p95LatencyMs(),
                result.p99LatencyMs()
        );
    }

    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) return seconds + " s";
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return String.format("%d m %d s", minutes, remainingSeconds);
    }
}