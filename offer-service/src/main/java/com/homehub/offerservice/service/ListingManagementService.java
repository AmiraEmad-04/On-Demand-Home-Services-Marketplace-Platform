package com.homehub.offerservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homehub.offerservice.config.RabbitConfig;
import com.homehub.offerservice.dto.OfferDtos;
import com.homehub.offerservice.entity.ServiceCategory;
import com.homehub.offerservice.entity.ServiceListing;
import com.homehub.offerservice.repository.ServiceCategoryRepository;
import com.homehub.offerservice.repository.ServiceListingRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Core business logic for managing service listings and categories.
 *
 * Key responsibilities:
 *  - CRUD for provider listings
 *  - Category management
 *  - Matching customer requests to available listings (team-of-3)
 *  - Publishing provider-alert events via RabbitMQ
 */
@Service
public class ListingManagementService {

    private final ServiceListingRepository listingRepo;
    private final ServiceCategoryRepository categoryRepo;
    private final RabbitTemplate rabbitTemplate;
    private final RestTemplate http;
    private final ObjectMapper mapper;

    @Value("${services.booking-service.base-url}")
    private String bookingServiceUrl;

    public ListingManagementService(ServiceListingRepository listingRepo,
                                    ServiceCategoryRepository categoryRepo,
                                    RabbitTemplate rabbitTemplate) {
        this.listingRepo    = listingRepo;
        this.categoryRepo   = categoryRepo;
        this.rabbitTemplate = rabbitTemplate;
        this.http           = new RestTemplate();
        this.mapper         = new ObjectMapper();
    }

    // ─────────────────────────────────────────────────────
    //  CATEGORY OPERATIONS
    // ─────────────────────────────────────────────────────

    public List<ServiceCategory> fetchAllCategories() {
        return categoryRepo.findAll();
    }

    /**
     * Adds a new category. Returns empty Optional if name already exists.
     */
    public Optional<ServiceCategory> registerCategory(String name) {
        if (categoryRepo.existsByNameIgnoreCase(name)) {
            return Optional.empty();
        }
        ServiceCategory cat = new ServiceCategory(name);
        return Optional.of(categoryRepo.save(cat));
    }

    // ─────────────────────────────────────────────────────
    //  LISTING CRUD
    // ─────────────────────────────────────────────────────

    /**
     * Provider posts a new service listing.
     */
    @Transactional
    public Optional<ServiceListing> publishListing(Long providerId, Long categoryId,
                                                    Double price, LocalDateTime availableDate,
                                                    String notes) {
        Optional<ServiceCategory> cat = categoryRepo.findById(categoryId);
        if (cat.isEmpty()) return Optional.empty();

        ServiceListing listing = new ServiceListing();
        listing.setProviderId(providerId);
        listing.setCategory(cat.get());
        listing.setPrice(price);
        listing.setAvailableDate(availableDate);
        listing.setNotes(notes);
        listing.setStatus(ServiceListing.ListingStatus.OPEN);

        return Optional.of(listingRepo.save(listing));
    }

    /**
     * Browse all OPEN listings, optionally filtered by category.
     */
    public List<ServiceListing> browseOpenListings(Long categoryId) {
        if (categoryId != null) {
            return listingRepo.findByCategoryAndStatus(categoryId, ServiceListing.ListingStatus.OPEN);
        }
        return listingRepo.findByStatus(ServiceListing.ListingStatus.OPEN);
    }

    /**
     * Get all listings for a specific provider (both OPEN and BOOKED).
     */
    public List<ServiceListing> fetchProviderListings(Long providerId) {
        return listingRepo.findByProviderId(providerId);
    }

    /**
     * Get listings that have been BOOKED (i.e., completed services) for a provider.
     */
    public List<ServiceListing> fetchCompletedListings(Long providerId) {
        return listingRepo.findByProviderIdAndStatus(providerId, ServiceListing.ListingStatus.BOOKED);
    }

    /**
     * Fetch a single listing by ID.
     */
    public Optional<ServiceListing> findListingById(Long offerId) {
        return listingRepo.findById(offerId);
    }

    /**
     * Provider updates price and/or available date.
     * Returns empty if listing not found or already booked.
     */
    @Transactional
    public Optional<ServiceListing> reviseListingDetails(Long offerId, Double newPrice,
                                                          LocalDateTime newDate, String newNotes) {
        Optional<ServiceListing> found = listingRepo.findById(offerId);
        if (found.isEmpty()) return Optional.empty();

        ServiceListing listing = found.get();
        if (listing.getStatus() == ServiceListing.ListingStatus.BOOKED) {
            return Optional.empty();  // can't revise a completed listing
        }

        if (newPrice != null)  listing.setPrice(newPrice);
        if (newDate  != null)  listing.setAvailableDate(newDate);
        if (newNotes != null)  listing.setNotes(newNotes);
        listing.setUpdatedAt(LocalDateTime.now());

        return Optional.of(listingRepo.save(listing));
    }

    /**
     * Called by Booking Service after a successful booking.
     * Marks the listing as BOOKED and records which customer/booking it belongs to.
     */
    @Transactional
    public Optional<ServiceListing> markAsBooked(Long offerId, Long customerId, Long bookingId) {
        Optional<ServiceListing> found = listingRepo.findById(offerId);
        if (found.isEmpty()) return Optional.empty();

        ServiceListing listing = found.get();
        if (listing.getStatus() != ServiceListing.ListingStatus.OPEN) {
            return Optional.empty();  // not available
        }

        listing.setStatus(ServiceListing.ListingStatus.BOOKED);
        listing.setAssignedCustomerId(customerId);
        listing.setLinkedBookingId(bookingId);
        listing.setUpdatedAt(LocalDateTime.now());

        return Optional.of(listingRepo.save(listing));
    }

    // ─────────────────────────────────────────────────────
    //  TEAM-OF-3: CUSTOMER REQUEST MATCHING
    // ─────────────────────────────────────────────────────

    /**
     * Attempt to match a customer's service request to an available listing.
     *
     * Match criteria:
     *   - Same category
     *   - Price <= customer's maxPrice
     *   - Available date falls on the same calendar day as requiredDate
     *
     * If a match is found, publishes a ServiceRequestMatched event to RabbitMQ
     * so the matched provider gets notified.
     *
     * @return the matched listing, or empty if no match
     */
    public Optional<ServiceListing> attemptMatchForRequest(Long customerId, Long categoryId,
                                                             Double maxPrice, LocalDateTime requiredDate) {
        // Search within a 24-hour window around the required date
        LocalDateTime dayStart = requiredDate.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd   = dayStart.plusDays(1).minusSeconds(1);

        List<ServiceListing> candidates = listingRepo
                .findMatchingListings(
                        categoryId,
                        ServiceListing.ListingStatus.OPEN,
                        maxPrice,
                        dayStart,
                        dayEnd
                );

        if (candidates.isEmpty()) return Optional.empty();

        // Pick cheapest available match
        ServiceListing bestMatch = candidates.stream()
                .min(Comparator.comparingDouble(ServiceListing::getPrice))
                .orElse(null);

        if (bestMatch == null) return Optional.empty();

        // Fire a RabbitMQ event so the provider knows a request matched their listing
        publishProviderMatchAlert(customerId, bestMatch);

        return Optional.of(bestMatch);
    }

    /**
     * Provider accepts a matched customer request.
     * Calls Booking Service to finalize the booking.
     *
     * Returns the booking response body as a Map, or empty on failure.
     */
    public Optional<Map<String, Object>> providerAcceptsRequest(Long offerId, Long providerId,
                                                                  Long customerId) {
        // Verify listing belongs to this provider and is still OPEN
        Optional<ServiceListing> found = listingRepo.findById(offerId);
        if (found.isEmpty()) return Optional.empty();

        ServiceListing listing = found.get();
        if (!listing.getProviderId().equals(providerId)) return Optional.empty();
        if (listing.getStatus() != ServiceListing.ListingStatus.OPEN) return Optional.empty();

        // Trigger Booking Service to create a booking (same flow as a normal booking)
        try {
            Map<String, Long> bookingPayload = new HashMap<>();
            bookingPayload.put("customerId", customerId);
            bookingPayload.put("offerId",    offerId);

            ResponseEntity<Map> response = http.postForEntity(
                    bookingServiceUrl + "/api/bookings",
                    bookingPayload,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return Optional.of(response.getBody());
            }
        } catch (Exception ex) {
            System.err.println("[OfferService] Could not call Booking Service: " + ex.getMessage());
        }

        return Optional.empty();
    }

    // ─────────────────────────────────────────────────────
    //  PRIVATE: RabbitMQ publishing
    // ─────────────────────────────────────────────────────

    private void publishProviderMatchAlert(Long customerId, ServiceListing matched) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventType",   "SERVICE_REQUEST_MATCHED");
            event.put("providerId",  matched.getProviderId());
            event.put("offerId",     matched.getOfferId());
            event.put("customerId",  customerId);
            event.put("category",    matched.getCategory().getName());
            event.put("price",       matched.getPrice());
            event.put("matchedAt",   LocalDateTime.now().toString());

            // Publish to the shared bookings fanout exchange
            rabbitTemplate.convertAndSend(RabbitConfig.BOOKINGS_EXCHANGE, "", event);
            System.out.println("[OfferService] Published SERVICE_REQUEST_MATCHED for offerId=" + matched.getOfferId());
        } catch (Exception ex) {
            System.err.println("[OfferService] RabbitMQ publish failed: " + ex.getMessage());
        }
    }
}
