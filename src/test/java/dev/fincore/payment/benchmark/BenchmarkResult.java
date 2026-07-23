package dev.fincore.payment.benchmark;

import java.math.BigDecimal;
import java.time.Duration;

public record BenchmarkResult(
        String strategy,
        int threads,
        int operationsPerThread,
        long success,
        long failed,
        long deadlocks,
        double tps,
        double avgLatencyMs,
        double p95LatencyMs,
        double p99LatencyMs,
        Duration duration,
        BigDecimal finalBalanceA,
        BigDecimal finalBalanceB
) {}
