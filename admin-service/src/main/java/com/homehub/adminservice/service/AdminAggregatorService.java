package com.homehub.adminservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Aggregates data from peer services via REST calls.
 *
 * Admin Service has NO database of its own — it delegates to:
 *   - User Service    → GET /users
 *   - Booking Service → GET /bookings/all
 *   - Offer Service   → POST /offers/categories
 */
@Service
public class AdminAggregatorService {

    private final RestTemplate http = new RestTemplate();

    @Value("${services.user-service.base-url}")
    private String userServiceUrl;

    @Value("${services.booking-service.base-url}")
    private String bookingServiceUrl;

    @Value("${services.offer-service.base-url}")
    private String offerServiceUrl;

    // ── View all registered users ──────────────────────────────────────────

    /**
     * Fetches all registered users from User Service.
     * GET http://localhost:8081/user-service/api/users
     */
    public List<Map<String, Object>> getAllUsers() {
        try {
            String url = userServiceUrl + "/users";
            ResponseEntity<List<Map<String, Object>>> resp = http.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {});
            return resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null
                    ? resp.getBody()
                    : Collections.emptyList();
        } catch (Exception ex) {
            System.err.println("[AdminService] getAllUsers failed: " + ex.getMessage());
            return Collections.emptyList();
        }
    }

    // ── View all transaction / booking history ─────────────────────────────

    /**
     * Fetches all booking records from Booking Service.
     * GET http://localhost:8084/api/bookings/all
     */
    public List<Map<String, Object>> getAllBookings() {
        try {
            String url = bookingServiceUrl + "/bookings/all";
            ResponseEntity<List<Map<String, Object>>> resp = http.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {});
            return resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null
                    ? resp.getBody()
                    : Collections.emptyList();
        } catch (Exception ex) {
            System.err.println("[AdminService] getAllBookings failed: " + ex.getMessage());
            return Collections.emptyList();
        }
    }

    // ── Add a new service category ─────────────────────────────────────────

    /**
     * Adds a new service category via Offer Service.
     * POST http://localhost:8083/api/offers/categories  { "name": "..." }
     *
     * Returns the response body from Offer Service (CategoryView or ErrorBody).
     */
    public ResponseEntity<Map<String, Object>> addCategory(String categoryName) {
        try {
            String url = offerServiceUrl + "/offers/categories";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> body = Map.of("name", categoryName);
            HttpEntity<Map<String, String>> req = new HttpEntity<>(body, headers);

            ResponseEntity<Map<String, Object>> resp = http.exchange(
                    url, HttpMethod.POST, req,
                    new ParameterizedTypeReference<>() {});
            return resp;
        } catch (Exception ex) {
            System.err.println("[AdminService] addCategory failed: " + ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Could not reach Offer Service: " + ex.getMessage()));
        }
    }

    // ── List all service categories ────────────────────────────────────────

    /**
     * Fetches all categories from Offer Service.
     * GET http://localhost:8083/api/offers/categories
     */
    public List<Map<String, Object>> getAllCategories() {
        try {
            String url = offerServiceUrl + "/offers/categories";
            ResponseEntity<List<Map<String, Object>>> resp = http.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {});
            return resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null
                    ? resp.getBody()
                    : Collections.emptyList();
        } catch (Exception ex) {
            System.err.println("[AdminService] getAllCategories failed: " + ex.getMessage());
            return Collections.emptyList();
        }
    }
}
