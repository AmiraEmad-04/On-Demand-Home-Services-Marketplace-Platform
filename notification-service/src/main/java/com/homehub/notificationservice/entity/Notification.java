package com.homehub.notificationservice.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Persisted notification record.
 *
 * Each RabbitMQ event that arrives creates one or more Notification rows —
 * one per recipient (customer, provider, or admin).
 *
 * userId = 0 is used for admin-targeted notifications.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    public enum NotificationType {
        BOOKING_CONFIRMED,   // booking succeeded
        PAYMENT_FAILED,      // payment failed — sent to customer
        SERVICE_MATCHED      // customer request matched to a provider offer
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user this notification is addressed to (customerId, providerId, or 0 for admin). */
    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    /** Human-readable message. */
    @Column(nullable = false, length = 512)
    private String message;

    /** Raw event data stored as JSON string for full transparency. */
    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ── Constructors ───────────────────────────────────────────────────────

    public Notification() {}

    public Notification(Long userId, NotificationType type,
                        String message, String payload) {
        this.userId    = userId;
        this.type      = type;
        this.message   = message;
        this.payload   = payload;
        this.createdAt = LocalDateTime.now();
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public Long getId()                    { return id; }
    public void setId(Long id)             { this.id = id; }

    public Long getUserId()                { return userId; }
    public void setUserId(Long userId)     { this.userId = userId; }

    public NotificationType getType()                  { return type; }
    public void setType(NotificationType type)         { this.type = type; }

    public String getMessage()             { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getPayload()             { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public LocalDateTime getCreatedAt()                { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt)  { this.createdAt = createdAt; }
}
