package com.homehub.offerservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.homehub.offerservice.entity.ServiceListing;
import java.time.LocalDateTime;

/**
 * All request/response shapes for Offer Service REST API.
 * Keeping DTOs in one file for clarity; split into separate files if it grows large.
 */
public class OfferDtos {

    // ── Inbound: create a listing ──────────────────────────────
    public static class CreateListingRequest {
        public Long providerId;
        public Long categoryId;
        public Double price;
        public LocalDateTime availableDate;
        public String notes;
    }

    // ── Inbound: update price / date ───────────────────────────
    public static class UpdateListingRequest {
        public Double price;           // null = no change
        public LocalDateTime availableDate;  // null = no change
        public String notes;           // null = no change
    }

    // ── Inbound: mark listing as booked ───────────────────────
    public static class CompleteListingRequest {
        public Long customerId;
        public Long bookingId;
    }

    // ── Inbound: add a new category (admin) ────────────────────
    public static class AddCategoryRequest {
        public String name;
    }

    // ── Inbound: customer service request (team-of-3) ──────────
    public static class CustomerServiceRequest {
        public Long customerId;
        public Long categoryId;
        public Double maxPrice;
        public LocalDateTime requiredDate;
        public String notes;
    }

    // ── Inbound: provider accepts a matched request ────────────
    public static class ProviderAcceptRequest {
        public Long providerId;
    }

    // ── Outbound: listing detail ───────────────────────────────
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ListingView {
        public Long offerId;
        public Long providerId;
        public Long categoryId;
        public String categoryName;
        public Double price;
        public LocalDateTime availableDate;
        public String notes;
        public String status;
        public Long assignedCustomerId;
        public Long linkedBookingId;
        public LocalDateTime createdAt;

        public static ListingView from(ServiceListing sl) {
            ListingView v = new ListingView();
            v.offerId            = sl.getOfferId();
            v.providerId         = sl.getProviderId();
            v.categoryId         = sl.getCategory().getCategoryId();
            v.categoryName       = sl.getCategory().getName();
            v.price              = sl.getPrice();
            v.availableDate      = sl.getAvailableDate();
            v.notes              = sl.getNotes();
            v.status             = sl.getStatus().name();
            v.assignedCustomerId = sl.getAssignedCustomerId();
            v.linkedBookingId    = sl.getLinkedBookingId();
            v.createdAt          = sl.getCreatedAt();
            return v;
        }
    }

    // ── Outbound: category ─────────────────────────────────────
    public static class CategoryView {
        public Long categoryId;
        public String name;
    }

    // ── Outbound: simple error wrapper ────────────────────────
    public static class ErrorBody {
        public String error;
        public ErrorBody(String msg) { this.error = msg; }
    }
}
