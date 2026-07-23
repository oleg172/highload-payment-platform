package dev.fincore.payment.benchmark;

import dev.fincore.payment.account.domain.Account;
import dev.fincore.payment.account.domain.repository.AccountRepository;
import dev.fincore.payment.transfer.api.dto.request.TransferRequest;
import dev.fincore.payment.transfer.service.TransferService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BenchmarkRunner {

    private final AccountRepository accountRepository;

    public BenchmarkRunner(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Запуск бенчмарка с моделированием горячих/холодных счетов.
     *
     * @param transferService   сервис для выполнения переводов
     * @param strategy          название стратегии (для отчёта)
     * @param threadCount       количество параллельных потоков
     * @param operationsPerThread количество операций на поток
     * @param totalAccounts     общее количество счетов
     * @param hotAccounts       количество горячих счетов (первые hotAccounts в списке)
     * @param hotProbability    вероятность (0..1), что перевод будет между горячими счетами
     * @param amountPerOperation сумма перевода
     * @param totalBalance      общая сумма на всех счетах (распределяется: 90% на горячие, 10% на холодные)
     * @return BenchmarkResult
     * @throws InterruptedException при прерывании потоков
     */
    public BenchmarkResult run(
            TransferService transferService,
            String strategy,
            int threadCount,
            int operationsPerThread,
            int totalAccounts,
            int hotAccounts,
            double hotProbability,
            BigDecimal amountPerOperation,
            BigDecimal totalBalance
    ) throws InterruptedException {

        log.info("=== Benchmark configuration ===");
        log.info("Strategy: {}", strategy);
        log.info("Threads: {}, ops/thread: {}", threadCount, operationsPerThread);
        log.info("Total accounts: {}, hot accounts: {}, hot probability: {}",
                totalAccounts, hotAccounts, hotProbability);
        log.info("Amount per op: {}, total balance: {}", amountPerOperation, totalBalance);

        // 1. Создаём счета с распределением баланса
        // 90% баланса на горячие счета, 10% на холодные
        BigDecimal hotShare = totalBalance.multiply(BigDecimal.valueOf(0.9));
        BigDecimal coldShare = totalBalance.multiply(BigDecimal.valueOf(0.1));

        BigDecimal hotInitial = hotShare.divide(BigDecimal.valueOf(hotAccounts), 2, BigDecimal.ROUND_HALF_EVEN);
        BigDecimal coldInitial = coldShare.divide(BigDecimal.valueOf(totalAccounts - hotAccounts), 2, BigDecimal.ROUND_HALF_EVEN);

        List<Account> accounts = new ArrayList<>(totalAccounts);
        for (int i = 0; i < hotAccounts; i++) {
            accounts.add(new Account("HOT_" + i, hotInitial));
        }
        for (int i = hotAccounts; i < totalAccounts; i++) {
            accounts.add(new Account("COLD_" + i, coldInitial));
        }
        accountRepository.saveAll(accounts);

        // Перезагружаем все счета, чтобы получить актуальные версии и ID
        List<Account> reloaded = new ArrayList<>();
        for (Account acc : accounts) {
            reloaded.add(accountRepository.findById(acc.getId()).orElseThrow());
        }

        // 2. Пул потоков и синхронизация
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger deadlockCount = new AtomicInteger(0);
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();

        // 3. Запускаем потоки
        Instant startTime = Instant.now();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < operationsPerThread; i++) {
                        long start = System.nanoTime();
                        try {
                            // Выбираем два разных счета согласно вероятности
                            int fromIdx, toIdx;
                            boolean hotTransfer = ThreadLocalRandom.current().nextDouble() < hotProbability;

                            if (hotTransfer) {
                                // Оба счета из горячего пула
                                fromIdx = ThreadLocalRandom.current().nextInt(hotAccounts);
                                do {
                                    toIdx = ThreadLocalRandom.current().nextInt(hotAccounts);
                                } while (toIdx == fromIdx);
                            } else {
                                // Любые два разных счета из всего пула
                                fromIdx = ThreadLocalRandom.current().nextInt(totalAccounts);
                                do {
                                    toIdx = ThreadLocalRandom.current().nextInt(totalAccounts);
                                } while (toIdx == fromIdx);
                            }

                            Account from = reloaded.get(fromIdx);
                            Account to = reloaded.get(toIdx);

                            TransferRequest request = new TransferRequest();
                            request.setFromAccountId(from.getId());
                            request.setToAccountId(to.getId());
                            request.setAmount(amountPerOperation);

                            transferService.transfer(request);

                            successCount.incrementAndGet();

                        } catch (ObjectOptimisticLockingFailureException e) {
                            failureCount.incrementAndGet();
                        } catch (PessimisticLockingFailureException e) {
                            deadlockCount.incrementAndGet();
                            failureCount.incrementAndGet();
                        } catch (Exception e) {
                            failureCount.incrementAndGet();
                        } finally {
                            long end = System.nanoTime();
                            latencies.add((end - start) / 1_000_000);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(10, TimeUnit.MINUTES);
        executor.shutdown();
        Instant endTime = Instant.now();

        if (!finished) {
            log.warn("Benchmark did not finish within timeout for {}", strategy);
        }

        // 4. Проверяем итоговую сумму
        BigDecimal finalTotal = BigDecimal.ZERO;
        for (Account acc : reloaded) {
            Account updated = accountRepository.findById(acc.getId()).orElseThrow();
            finalTotal = finalTotal.add(updated.getBalance());
        }

        // Для наглядности возьмём первый горячий и первый холодный счета
        Account hotSample = accountRepository.findById(reloaded.get(0).getId()).orElseThrow();
        Account coldSample = accountRepository.findById(reloaded.get(hotAccounts).getId()).orElseThrow();

        long totalSuccessful = successCount.get();
        long totalFailed = failureCount.get();
        long totalDeadlocks = deadlockCount.get();
        Duration duration = Duration.between(startTime, endTime);
        double tps = duration.getSeconds() == 0 ? 0 : (double) totalSuccessful / duration.getSeconds();

        // 5. Расчёт латентности
        List<Long> latencyList = new ArrayList<>(latencies);
        Collections.sort(latencyList);
        double avgLatency = latencyList.stream().mapToLong(Long::longValue).average().orElse(0);
        double p95 = percentile(latencyList, 0.95);
        double p99 = percentile(latencyList, 0.99);

        log.info("Benchmark completed. Success: {}, Failed: {}, Deadlocks: {}, TPS: {:.2f}",
                totalSuccessful, totalFailed, totalDeadlocks, tps);

        return new BenchmarkResult(
                strategy,
                threadCount,
                operationsPerThread,
                totalSuccessful,
                totalFailed,
                totalDeadlocks,
                tps,
                avgLatency,
                p95,
                p99,
                duration,
                hotSample.getBalance(),
                coldSample.getBalance()
        );
    }

    private double percentile(List<Long> sortedList, double percentile) {
        if (sortedList.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile * sortedList.size()) - 1;
        index = Math.max(0, Math.min(index, sortedList.size() - 1));
        return sortedList.get(index);
    }
}
