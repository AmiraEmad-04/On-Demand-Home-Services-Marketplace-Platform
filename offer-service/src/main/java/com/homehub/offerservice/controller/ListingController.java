package com.homehub.offerservice.controller;

import com.homehub.offerservice.dto.OfferDtos;
import com.homehub.offerservice.entity.ServiceCategory;
import com.homehub.offerservice.entity.ServiceListing;
import com.homehub.offerservice.service.ListingManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST controller for the Offer Service.
 * Base path: /api/offers
 *
 * Endpoints:
 *   POST   /api/offers                          → provider creates a listing
 *   GET    /api/offers                          → browse open listings (optional ?categoryId=)
 *   GET    /api/offers/{offerId}                → get single listing (used by Booking Service)
 *   PATCH  /api/offers/{offerId}                → provider updates price/date
 *   PATCH  /api/offers/{offerId}/complete       → Booking Service marks listing booked
 *   GET    /api/offers/provider/{providerId}    → provider views all their listings
 *   GET    /api/offers/provider/{providerId}/completed → provider sees booked services
 *   GET    /api/offers/categories               → list all categories
 *   POST   /api/offers/categories               → admin adds a category
 *   POST   /api/offers/match                    → customer posts a service request (team-of-3)
 *   POST   /api/offers/{offerId}/accept         → provider accepts a matched request (team-of-3)
 */
@RestController
@RequestMapping("/api/offers")
public class ListingController {

    private final ListingManagementService svc;

    public ListingController(ListingManagementService svc) {
        this.svc = svc;
    }

    // ─────────────────────────────────────────────────────
    //  CATEGORY ENDPOINTS
    // ─────────────────────────────────────────────────────

    /** GET /api/offers/categories — list all categories */
    @GetMapping("/categories")
    public ResponseEntity<List<OfferDtos.CategoryView>> listCategories() {
        List<OfferDtos.CategoryView> views = svc.fetchAllCategories().stream()
                .map(c -> {
                    OfferDtos.CategoryView v = new OfferDtos.CategoryView();
                    v.categoryId = c.getCategoryId();
                    v.name       = c.getName();
                    return v;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(views);
    }

    /** POST /api/offers/categories — admin registers a new trade category */
    @PostMapping("/categories")
    public ResponseEntity<?> addCategory(@RequestBody OfferDtos.AddCategoryRequest req) {
        if (req.name == null || req.name.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new OfferDtos.ErrorBody("Category name must not be blank"));
        }

        Optional<ServiceCategory> created = svc.registerCategory(req.name.trim());
        if (created.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new OfferDtos.ErrorBody("Category already exists: " + req.name));
        }

        OfferDtos.CategoryView view = new OfferDtos.CategoryView();
        view.categoryId = created.get().getCategoryId();
        view.name       = created.get().getName();
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    // ─────────────────────────────────────────────────────
    //  LISTING ENDPOINTS
    // ─────────────────────────────────────────────────────

    /** POST /api/offers — provider posts a new service listing */
    @PostMapping
    public ResponseEntity<?> createListing(@RequestBody OfferDtos.CreateListingRequest req) {
        if (req.providerId == null || req.categoryId == null
                || req.price == null || req.availableDate == null) {
            return ResponseEntity.badRequest()
                    .body(new OfferDtos.ErrorBody("providerId, categoryId, price, availableDate are required"));
        }
        if (req.price <= 0) {
            return ResponseEntity.badRequest()
                    .body(new OfferDtos.ErrorBody("price must be greater than zero"));
        }

        Optional<ServiceListing> listing = svc.publishListing(
                req.providerId, req.categoryId, req.price, req.availableDate, req.notes);

        if (listing.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new OfferDtos.ErrorBody("Category not found: " + req.categoryId));
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OfferDtos.ListingView.from(listing.get()));
    }

    /**
     * GET /api/offers?categoryId={id} — browse OPEN listings.
     * categoryId is optional; omit to see all.
     */
    @GetMapping
    public ResponseEntity<List<OfferDtos.ListingView>> browseListings(
            @RequestParam(required = false) Long categoryId) {

        List<OfferDtos.ListingView> views = svc.browseOpenListings(categoryId).stream()
                .map(OfferDtos.ListingView::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(views);
    }

    /** GET /api/offers/{offerId} — fetch single listing (Booking Service uses this) */
    @GetMapping("/{offerId}")
    public ResponseEntity<?> getListing(@PathVariable Long offerId) {
        Optional<ServiceListing> found = svc.findListingById(offerId);
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new OfferDtos.ErrorBody("Listing not found: " + offerId));
        }
        return ResponseEntity.ok(OfferDtos.ListingView.from(found.get()));
    }

    /** PATCH /api/offers/{offerId} — provider revises price and/or available date */
    @PatchMapping("/{offerId}")
    public ResponseEntity<?> reviseListing(@PathVariable Long offerId,
                                            @RequestBody OfferDtos.UpdateListingRequest req) {
        Optional<ServiceListing> updated = svc.reviseListingDetails(
                offerId, req.price, req.availableDate, req.notes);

        if (updated.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new OfferDtos.ErrorBody("Listing not found or already booked: " + offerId));
        }
        return ResponseEntity.ok(OfferDtos.ListingView.from(updated.get()));
    }

    /**
     * PATCH /api/offers/{offerId}/complete — called by Booking Service after payment succeeds.
     * Body: { "customerId": 1, "bookingId": 5 }
     */
    @PatchMapping("/{offerId}/complete")
    public ResponseEntity<?> completeListing(@PathVariable Long offerId,
                                              @RequestBody OfferDtos.CompleteListingRequest req) {
        if (req.customerId == null || req.bookingId == null) {
            return ResponseEntity.badRequest()
                    .body(new OfferDtos.ErrorBody("customerId and bookingId are required"));
        }

        Optional<ServiceListing> updated = svc.markAsBooked(offerId, req.customerId, req.bookingId);
        if (updated.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new OfferDtos.ErrorBody("Listing not found or already booked: " + offerId));
        }
        return ResponseEntity.ok(OfferDtos.ListingView.from(updated.get()));
    }

    /** GET /api/offers/provider/{providerId} — all listings by this provider */
    @GetMapping("/provider/{providerId}")
    public ResponseEntity<List<OfferDtos.ListingView>> providerListings(
            @PathVariable Long providerId) {

        List<OfferDtos.ListingView> views = svc.fetchProviderListings(providerId).stream()
                .map(OfferDtos.ListingView::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(views);
    }

    /** GET /api/offers/provider/{providerId}/completed — listings that became bookings */
    @GetMapping("/provider/{providerId}/completed")
    public ResponseEntity<List<OfferDtos.ListingView>> completedListings(
            @PathVariable Long providerId) {

        List<OfferDtos.ListingView> views = svc.fetchCompletedListings(providerId).stream()
                .map(OfferDtos.ListingView::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(views);
    }

    // ─────────────────────────────────────────────────────
    //  TEAM-OF-3 ENDPOINTS
    // ─────────────────────────────────────────────────────

    /**
     * POST /api/offers/match — customer posts a service request.
     * System tries to match it to an available listing.
     * If matched, a RabbitMQ alert is sent to the provider.
     */
    @PostMapping("/match")
    public ResponseEntity<?> matchRequest(@RequestBody OfferDtos.CustomerServiceRequest req) {
        if (req.customerId == null || req.categoryId == null
                || req.maxPrice == null || req.requiredDate == null) {
            return ResponseEntity.badRequest()
                    .body(new OfferDtos.ErrorBody(
                            "customerId, categoryId, maxPrice, requiredDate are required"));
        }

        Optional<ServiceListing> match = svc.attemptMatchForRequest(
                req.customerId, req.categoryId, req.maxPrice, req.requiredDate);

        if (match.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new OfferDtos.ErrorBody(
                            "No available listing matches your request criteria"));
        }

        // Return the matched listing so the customer can see what was found
        return ResponseEntity.ok(Map.of(
                "message",       "Match found — provider has been notified",
                "matchedOffer",  OfferDtos.ListingView.from(match.get())
        ));
    }

    /**
     * POST /api/offers/{offerId}/accept — provider accepts a matched customer request.
     * Calls Booking Service to trigger the full booking flow.
     */
    @PostMapping("/{offerId}/accept")
    public ResponseEntity<?> acceptRequest(@PathVariable Long offerId,
                                            @RequestBody OfferDtos.ProviderAcceptRequest req,
                                            @RequestParam Long customerId) {
        if (req.providerId == null) {
            return ResponseEntity.badRequest()
                    .body(new OfferDtos.ErrorBody("providerId is required"));
        }

        Optional<Map<String, Object>> bookingResult =
                svc.providerAcceptsRequest(offerId, req.providerId, customerId);

        if (bookingResult.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new OfferDtos.ErrorBody(
                            "Could not create booking — listing unavailable or booking service error"));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(bookingResult.get());
    }
}
