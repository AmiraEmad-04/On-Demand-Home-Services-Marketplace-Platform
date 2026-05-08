package com.homehub.adminservice.controller;

import com.homehub.adminservice.messaging.AdminPaymentFailureListener;
import com.homehub.adminservice.service.AdminAggregatorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin REST controller.
 *
 * Base path: /api/admin
 *
 * Endpoints:
 *   GET  /api/admin/users              → view all registered users
 *   GET  /api/admin/bookings           → view all booking / transaction history
 *   GET  /api/admin/categories         → list all service categories
 *   POST /api/admin/categories         → add a new service category
 *   GET  /api/admin/alerts             → view payment failure alerts (RabbitMQ)
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminAggregatorService aggregator;
    private final AdminPaymentFailureListener alertListener;

    public AdminController(AdminAggregatorService aggregator,
                           AdminPaymentFailureListener alertListener) {
        this.aggregator    = aggregator;
        this.alertListener = alertListener;
    }

    // ── GET /api/admin/users ───────────────────────────────────────────────

    /**
     * View all registered users.
     * Delegates to: GET http://localhost:8081/user-service/api/users
     */
    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        List<Map<String, Object>> users = aggregator.getAllUsers();
        return ResponseEntity.ok(users);
    }

    // ── GET /api/admin/bookings ────────────────────────────────────────────

    /**
     * View all transaction history (all booking records).
     * Delegates to: GET http://localhost:8084/api/bookings/all
     */
    @GetMapping("/bookings")
    public ResponseEntity<List<Map<String, Object>>> getAllBookings() {
        List<Map<String, Object>> bookings = aggregator.getAllBookings();
        return ResponseEntity.ok(bookings);
    }

    // ── GET /api/admin/categories ──────────────────────────────────────────

    /**
     * List all service categories.
     * Delegates to: GET http://localhost:8083/api/offers/categories
     */
    @GetMapping("/categories")
    public ResponseEntity<List<Map<String, Object>>> getAllCategories() {
        List<Map<String, Object>> categories = aggregator.getAllCategories();
        return ResponseEntity.ok(categories);
    }

    // ── POST /api/admin/categories ─────────────────────────────────────────

    /**
     * Add a new service category.
     * Body: { "name": "Gardening" }
     * Delegates to: POST http://localhost:8083/api/offers/categories
     */
    @PostMapping("/categories")
    public ResponseEntity<Map<String, Object>> addCategory(
            @RequestBody Map<String, String> body) {

        String name = body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Category name must not be blank"));
        }

        ResponseEntity<Map<String, Object>> upstream = aggregator.addCategory(name.trim());
        // Relay the exact status code from Offer Service (201 Created, 409 Conflict, etc.)
        return ResponseEntity.status(upstream.getStatusCode()).body(upstream.getBody());
    }

    // ── GET /api/admin/alerts ──────────────────────────────────────────────

    /**
     * View all payment failure alerts received via RabbitMQ.
     * These are PAYMENT_FAILED events routed to admin.notification.queue
     * via payments.exchange (direct, key: "payment.failed").
     *
     * Team-of-3 feature: admin is notified of every payment failure.
     */
    @GetMapping("/alerts")
    public ResponseEntity<List<Map<String, Object>>> getPaymentAlerts() {
        return ResponseEntity.ok(alertListener.getAlerts());
    }
}
