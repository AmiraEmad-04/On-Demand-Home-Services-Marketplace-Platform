package com.marketplace.walletservice.beans;

import com.marketplace.walletservice.entities.Wallet;
import jakarta.ejb.Stateful;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.List;

/*
 * @Stateful means:
 * - This bean REMEMBERS state between calls for the same session
 * - Like a shopping cart — it keeps your items until you checkout
 * - Each client gets their OWN instance of this bean
 *
 * In our case: it remembers the pending top-up amount
 * during a session before committing to the database
 */
@Stateful
public class WalletSessionBean {

    @PersistenceContext(unitName = "WalletPU")
    private EntityManager em;

    /*
     * This is the "session state" — remembered between calls.
     * Tracks how much is being added in this session.
     * This is what makes it @Stateful — a @Stateless bean
     * could never remember this between calls!
     */
    private Double pendingTopUp = 0.0;

    /*
     * CREATE a new wallet for a user.
     * Called when a new customer registers.
     * initialBalance = how much they start with e.g. 200.00
     */
    public Wallet createWallet(Long userId, Double initialBalance) {
        // Check if wallet already exists for this user
        List<Wallet> existing = em.createQuery(
                        "SELECT w FROM Wallet w WHERE w.userId = :userId",
                        Wallet.class)
                .setParameter("userId", userId)
                .getResultList();

        if (!existing.isEmpty()) {
            return existing.get(0); // already has a wallet
        }

        Wallet wallet = new Wallet(userId, initialBalance);
        em.persist(wallet);
        return wallet;
    }

    /*
     * GET wallet balance for a user.
     * Returns the wallet object with userId and balance.
     */
    public Wallet getWallet(Long userId) {
        List<Wallet> results = em.createQuery(
                        "SELECT w FROM Wallet w WHERE w.userId = :userId",
                        Wallet.class)
                .setParameter("userId", userId)
                .getResultList();

        if (results.isEmpty()) {
            return null;
        }
        return results.get(0);
    }

    /*
     * Stage a top-up amount in session memory.
     * The money isn't added to DB yet — just remembered.
     * This shows @Stateful in action!
     */
    public void stageTopUp(Double amount) {
        this.pendingTopUp += amount;
        System.out.println("Staged top-up: " + amount +
                " | Total pending: " + pendingTopUp);
    }

    /*
     * Commit the staged top-up to the database.
     * Now the money is actually added to the wallet.
     */
    public Wallet commitTopUp(Long userId) {
        Wallet wallet = getWallet(userId);
        if (wallet == null) return null;

        wallet.setBalance(wallet.getBalance() + pendingTopUp);
        em.merge(wallet); // UPDATE wallets SET balance = ? WHERE userId = ?
        pendingTopUp = 0.0; // reset session state
        return wallet;
    }

    /*
     * Simple direct top-up — adds amount immediately.
     * This is what the REST endpoint calls directly.
     */
    public Wallet topUp(Long userId, Double amount) {
        Wallet wallet = getWallet(userId);
        if (wallet == null) return null;

        wallet.setBalance(wallet.getBalance() + amount);
        em.merge(wallet);
        return wallet;
    }

    /*
     * DEDUCT money from wallet.
     * Called by REST endpoint from Booking Service.
     * Returns null if insufficient balance.
     */
    public Wallet deduct(Long userId, Double amount) {
        Wallet wallet = getWallet(userId);
        if (wallet == null) return null;

        // Check sufficient balance
        if (wallet.getBalance() < amount) {
            return null; // not enough money
        }

        wallet.setBalance(wallet.getBalance() - amount);
        em.merge(wallet);
        return wallet;
    }

    /*
     * ROLLBACK — refund money back to wallet.
     * Called when booking fails after deduction.
     */
    public Wallet rollback(Long userId, Double amount) {
        Wallet wallet = getWallet(userId);
        if (wallet == null) return null;

        wallet.setBalance(wallet.getBalance() + amount);
        em.merge(wallet);
        System.out.println("Rollback: returned " + amount +
                " to userId " + userId);
        return wallet;
    }

    /*
     * Get pending top-up amount (session state).
     * Shows what's staged but not yet committed.
     */
    public Double getPendingTopUp() {
        return pendingTopUp;
    }
}