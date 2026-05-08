package com.homehub.notificationservice.controller;

import com.homehub.notificationservice.entity.Notification;
import com.homehub.notificationservice.repository.NotificationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Notification REST controller.
 *
 * Base path: /api/notifications
 *
 * Endpoints:
 *   GET /api/notifications/{userId}   → all notifications for a user (JSON)
 *   GET /api/notifications            → all notifications (admin view)
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository repo;

    public NotificationController(NotificationRepository repo) {
        this.repo = repo;
    }

    // ── GET /api/notifications/{userId} ───────────────────────────────────

    /**
     * Returns all notifications for the given userId, newest first.
     *
     * Used by:
     *   - Customers to see booking confirmations and payment failure alerts
     *   - Providers to see new booking confirmations and matched service requests
     *
     * Response is JSON array of notification objects.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<List<Notification>> getNotificationsForUser(
            @PathVariable Long userId) {
        List<Notification> notifications =
                repo.findByUserIdOrderByCreatedAtDesc(userId);
        return ResponseEntity.ok(notifications);
    }

    // ── GET /api/notifications ─────────────────────────────────────────────

    /**
     * Returns all notifications across all users, newest first.
     * Intended for admin/debugging use.
     */
    @GetMapping
    public ResponseEntity<List<Notification>> getAllNotifications() {
        List<Notification> notifications =
                repo.findAllByOrderByCreatedAtDesc();
        return ResponseEntity.ok(notifications);
    }

    // ── GET /api/notifications/health ─────────────────────────────────────

    /**
     * Simple health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "service", "notification-service",
                "status",  "UP"
        ));
    }
}
