package com.homehub.notificationservice.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homehub.notificationservice.config.NotificationRabbitConfig;
import com.homehub.notificationservice.entity.Notification;
import com.homehub.notificationservice.repository.NotificationRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumes RabbitMQ events and persists Notification records.
 *
 * Three listener methods, one per queue:
 *
 *   onBookingEvent()   — notification.queue (bookings.exchange fanout)
 *                        Handles: BOOKING_CONFIRMED, SERVICE_REQUEST_MATCHED
 *
 *   onPaymentFailed()  — customer.notification.queue (payments.exchange direct)
 *                        Handles: PAYMENT_FAILED
 *
 *   onProviderMatch()  — provider.match.queue (bookings.exchange fanout)
 *                        Handles: SERVICE_REQUEST_MATCHED (provider-side alert)
 *
 * Note: notification.queue and provider.match.queue are both bound to the
 * bookings.exchange fanout, so they receive the same messages. We use
 * notification.queue for customer/general notifications and provider.match.queue
 * specifically for provider-targeted SERVICE_REQUEST_MATCHED alerts.
 */
@Component
public class NotificationEventListener {

    private final NotificationRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();

    public NotificationEventListener(NotificationRepository repo) {
        this.repo = repo;
    }

    // ── 1. Booking events (fanout → notification.queue) ───────────────────

    /**
     * Handles BOOKING_CONFIRMED and SERVICE_REQUEST_MATCHED from bookings.exchange.
     * Creates notifications for both the customer and the provider.
     */
    @RabbitListener(queues = NotificationRabbitConfig.NOTIFICATION_QUEUE)
    public void onBookingEvent(Map<String, Object> event) {
        try {
            String eventType = (String) event.get("eventType");
            String payload   = mapper.writeValueAsString(event);

            System.out.println("[NotificationService] Received on notification.queue: " + eventType);

            if ("BOOKING_CONFIRMED".equals(eventType)) {
                handleBookingConfirmed(event, payload);
            } else if ("SERVICE_REQUEST_MATCHED".equals(eventType)) {
                handleServiceMatchedForCustomer(event, payload);
            }
            // PAYMENT_FAILED arrives on customer.notification.queue — handled separately
        } catch (Exception ex) {
            System.err.println("[NotificationService] onBookingEvent error: " + ex.getMessage());
        }
    }

    // ── 2. Payment failed (direct → customer.notification.queue) ─────────

    /**
     * Handles PAYMENT_FAILED from payments.exchange.
     * Creates a notification for the customer.
     */
    @RabbitListener(queues = NotificationRabbitConfig.CUSTOMER_NOTIFY_QUEUE)
    public void onPaymentFailed(Map<String, Object> event) {
        try {
            String payload    = mapper.writeValueAsString(event);
            Long   customerId = toLong(event.get("customerId"));
            String reason     = (String) event.getOrDefault("reason", "UNKNOWN");
            Object amount     = event.get("attemptedAmount");

            System.out.println("[NotificationService] PAYMENT_FAILED for customerId=" + customerId);

            String msg = String.format(
                    "Your booking attempt failed. Reason: %s. Amount: %s was NOT charged.",
                    reason, amount);

            repo.save(new Notification(customerId, Notification.NotificationType.PAYMENT_FAILED,
                    msg, payload));
        } catch (Exception ex) {
            System.err.println("[NotificationService] onPaymentFailed error: " + ex.getMessage());
        }
    }

    // ── 3. Provider match alert (fanout → provider.match.queue) ──────────

    /**
     * Handles SERVICE_REQUEST_MATCHED from bookings.exchange (provider side).
     * Creates a notification for the matched provider.
     */
    @RabbitListener(queues = NotificationRabbitConfig.PROVIDER_MATCH_QUEUE)
    public void onProviderMatch(Map<String, Object> event) {
        try {
            String eventType = (String) event.get("eventType");
            if (!"SERVICE_REQUEST_MATCHED".equals(eventType)) {
                // This queue also receives BOOKING_CONFIRMED — skip those here
                return;
            }

            String payload    = mapper.writeValueAsString(event);
            Long   providerId = toLong(event.get("providerId"));
            Object category   = event.get("category");
            Object price      = event.get("price");
            Object offerId    = event.get("offerId");

            System.out.println("[NotificationService] SERVICE_REQUEST_MATCHED for providerId=" + providerId);

            String msg = String.format(
                    "A customer has requested your service! Category: %s, Price: %s, OfferId: %s. " +
                    "Accept via POST /api/offers/%s/accept",
                    category, price, offerId, offerId);

            repo.save(new Notification(providerId, Notification.NotificationType.SERVICE_MATCHED,
                    msg, payload));
        } catch (Exception ex) {
            System.err.println("[NotificationService] onProviderMatch error: " + ex.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private void handleBookingConfirmed(Map<String, Object> event, String payload) {
        Long   customerId  = toLong(event.get("customerId"));
        Long   providerId  = toLong(event.get("providerId"));
        Object bookingId   = event.get("bookingId");
        Object amount      = event.get("amount");

        // Notify customer
        String customerMsg = String.format(
                "Your booking #%s has been confirmed! Amount charged: %s.", bookingId, amount);
        repo.save(new Notification(customerId, Notification.NotificationType.BOOKING_CONFIRMED,
                customerMsg, payload));

        // Notify provider
        String providerMsg = String.format(
                "You have a new confirmed booking #%s! Customer ID: %s, Amount: %s.",
                bookingId, customerId, amount);
        repo.save(new Notification(providerId, Notification.NotificationType.BOOKING_CONFIRMED,
                providerMsg, payload));

        System.out.println("[NotificationService] BOOKING_CONFIRMED notifications saved for " +
                "customerId=" + customerId + " providerId=" + providerId);
    }

    private void handleServiceMatchedForCustomer(Map<String, Object> event, String payload) {
        Long   customerId = toLong(event.get("customerId"));
        Object offerId    = event.get("offerId");
        Object category   = event.get("category");
        Object price      = event.get("price");

        String msg = String.format(
                "Your service request has been matched! Category: %s, Price: %s, OfferId: %s. " +
                "Waiting for provider to accept.",
                category, price, offerId);

        repo.save(new Notification(customerId, Notification.NotificationType.SERVICE_MATCHED,
                msg, payload));

        System.out.println("[NotificationService] SERVICE_MATCHED notification saved for customerId=" + customerId);
    }

    private Long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.parseLong(value.toString());
    }
}
