package com.marketplace.walletservice.entities;

import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.persistence.*;

/*
 * This class represents the "wallets" table in MySQL.
 * Each customer has one wallet with a balance.
 */
@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Links this wallet to a user in the User Service
    // Each userId should be unique — one wallet per user
    @Column(unique = true, nullable = false)
    @JsonbProperty("userId")
    private Long userId;

    // The money in the wallet e.g. 200.00
    @Column(nullable = false)
    private Double balance;

    // --- Constructors ---
    public Wallet() {}

    public Wallet(Long userId, Double balance) {
        this.userId = userId;
        this.balance = balance;
    }

    // --- Getters and Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Double getBalance() { return balance; }
    public void setBalance(Double balance) { this.balance = balance; }
}