package com.marketplace.userservice.entities;

import jakarta.persistence.*;

/*
 * Stores a customer's service request.
 * Customer says: "I need a Plumber, max $100, on 2026-05-10"
 * System will match this to an available offer (done by M2)
 */
@Entity
@Table(name = "service_requests")
public class ServiceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Which customer made this request
    @Column(nullable = false)
    private Long customerId;

    // e.g. "Plumbing", "Carpentry"
    @Column(nullable = false)
    private String category;

    // Maximum price customer is willing to pay
    @Column(nullable = false)
    private Double maxPrice;

    // Date they need the service e.g. "2026-05-10"
    @Column(nullable = false)
    private String requiredDate;

    // PENDING = waiting for match, MATCHED = offer found
    @Column(nullable = false)
    private String status = "PENDING";

    // Filled in by M2 when a match is found
    private Long matchedOfferId;

    // --- Constructors ---
    public ServiceRequest() {}

    public ServiceRequest(Long customerId, String category,
                          Double maxPrice, String requiredDate) {
        this.customerId = customerId;
        this.category = category;
        this.maxPrice = maxPrice;
        this.requiredDate = requiredDate;
        this.status = "PENDING";
    }

    // --- Getters and Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Double getMaxPrice() { return maxPrice; }
    public void setMaxPrice(Double maxPrice) { this.maxPrice = maxPrice; }

    public String getRequiredDate() { return requiredDate; }
    public void setRequiredDate(String requiredDate) { this.requiredDate = requiredDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getMatchedOfferId() { return matchedOfferId; }
    public void setMatchedOfferId(Long matchedOfferId) { this.matchedOfferId = matchedOfferId; }
}