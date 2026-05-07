package com.homehub.bookingservice.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents a confirmed (or rejected) reservation between a customer and a provider.
 *
 * A Reservation is created by the Booking Service after:
 *   1. Verifying the listing is OPEN (REST → Offer Service)
 *   2. Checking wallet balance (REST → Wallet Service)
 *   3. Deducting payment (REST → Wallet Service)
 *   4. Marking listing as BOOKED (REST → Offer Service)
 *   5. Publishing a BookingConfirmed or PaymentFailed event (RabbitMQ)
 */
@Entity
@Table(name = "reservations")
public class Reservation {

    public enum ReservationStatus {
        CONFIRMED,  // booking succeeded, payment taken
        REJECTED    // booking failed (insufficient balance / offer unavailable)
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookingId;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false)
    private Long offerId;

    /** Denormalised from Offer Service at booking time — avoids extra REST call for reads */
    @Column(nullable = false)
    private Long providerId;

    @Column(nullable = false)
    private Double chargedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    /** Human-readable reason for rejection, null when CONFIRMED */
    private String rejectionReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime reservedAt = LocalDateTime.now();

    // ── getters / setters ──

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public Long getOfferId() { return offerId; }
    public void setOfferId(Long offerId) { this.offerId = offerId; }

    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }

    public Double getChargedAmount() { return chargedAmount; }
    public void setChargedAmount(Double chargedAmount) { this.chargedAmount = chargedAmount; }

    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus status) { this.status = status; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public LocalDateTime getReservedAt() { return reservedAt; }
    public void setReservedAt(LocalDateTime reservedAt) { this.reservedAt = reservedAt; }
}
