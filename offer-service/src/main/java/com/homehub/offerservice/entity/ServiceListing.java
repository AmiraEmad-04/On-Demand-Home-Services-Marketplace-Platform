package com.homehub.offerservice.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A service listing posted by a provider (plumber, carpenter, etc.).
 * Customers browse these and choose one to book.
 *
 * Lifecycle:  OPEN -> BOOKED (once a booking is confirmed)
 */
@Entity
@Table(name = "service_listings")
public class ServiceListing {

    public enum ListingStatus {
        OPEN,       // Available for booking
        BOOKED      // Already claimed by a customer
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long offerId;

    /** Foreign key to User Service — stored here for query purposes */
    @Column(nullable = false)
    private Long providerId;

    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    private ServiceCategory category;

    @Column(nullable = false)
    private Double price;

    /** The date/time the provider is available to perform the service */
    @Column(nullable = false)
    private LocalDateTime availableDate;

    @Column(length = 512)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingStatus status = ListingStatus.OPEN;

    /** Populated when listing becomes BOOKED */
    private Long assignedCustomerId;
    private Long linkedBookingId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    // ── getters / setters ──

    public Long getOfferId() { return offerId; }
    public void setOfferId(Long offerId) { this.offerId = offerId; }

    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }

    public ServiceCategory getCategory() { return category; }
    public void setCategory(ServiceCategory category) { this.category = category; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public LocalDateTime getAvailableDate() { return availableDate; }
    public void setAvailableDate(LocalDateTime availableDate) { this.availableDate = availableDate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public ListingStatus getStatus() { return status; }
    public void setStatus(ListingStatus status) { this.status = status; }

    public Long getAssignedCustomerId() { return assignedCustomerId; }
    public void setAssignedCustomerId(Long assignedCustomerId) { this.assignedCustomerId = assignedCustomerId; }

    public Long getLinkedBookingId() { return linkedBookingId; }
    public void setLinkedBookingId(Long linkedBookingId) { this.linkedBookingId = linkedBookingId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
