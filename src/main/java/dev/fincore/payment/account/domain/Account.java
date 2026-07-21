package dev.fincore.payment.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account")
public class Account {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String number;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected Account() {
    }

    public Account(String number) {
        this.id = UUID.randomUUID();
        this.number = number;
        this.balance = BigDecimal.ZERO;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public BigDecimal getBalance() {
        return balance.setScale(2);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
