package com.homehub.bookingservice.messaging;

import com.homehub.bookingservice.config.BookingRabbitConfig;
import com.homehub.bookingservice.entity.Reservation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Responsible for publishing all RabbitMQ events originating from the Booking Service.
 *
 * Two exchanges:
 *   bookings.exchange  (fanout) → BookingConfirmed events
 *   payments.exchange  (direct) → PaymentFailed events, routing key "payment.failed"
 *
 * The Notification Service (M3) and Wallet MDB (M1) consume from these exchanges.
 */
@Component
public class ReservationEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public ReservationEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Publish a BookingConfirmed event to the fanout exchange.
     * Both the customer and provider will be notified by Notification Service.
     */
    public void emitBookingConfirmed(Reservation reservation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType",    "BOOKING_CONFIRMED");
        payload.put("bookingId",    reservation.getBookingId());
        payload.put("customerId",   reservation.getCustomerId());
        payload.put("providerId",   reservation.getProviderId());
        payload.put("offerId",      reservation.getOfferId());
        payload.put("amount",       reservation.getChargedAmount());
        payload.put("bookedAt",     reservation.getReservedAt().toString());

        try {
            // Empty routing key for fanout — the exchange ignores it
            rabbitTemplate.convertAndSend(
                    BookingRabbitConfig.BOOKINGS_EXCHANGE, "", payload);
            System.out.println("[BookingService] BOOKING_CONFIRMED published for bookingId="
                    + reservation.getBookingId());
        } catch (Exception ex) {
            // Booking already saved — notify failure is non-fatal
            System.err.println("[BookingService] Failed to publish BOOKING_CONFIRMED: "
                    + ex.getMessage());
        }
    }

    /**
     * Publish a PaymentFailed event to the direct exchange.
     * Routing key "payment.failed" sends it to both the customer queue
     * and the admin queue (as configured in BookingRabbitConfig).
     */
    public void emitPaymentFailed(Long customerId, Long offerId,
                                   Double attemptedAmount, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType",       "PAYMENT_FAILED");
        payload.put("customerId",      customerId);
        payload.put("offerId",         offerId);
        payload.put("attemptedAmount", attemptedAmount);
        payload.put("reason",          reason);
        payload.put("attemptedAt",     LocalDateTime.now().toString());

        try {
            rabbitTemplate.convertAndSend(
                    BookingRabbitConfig.PAYMENTS_EXCHANGE,
                    BookingRabbitConfig.PAYMENT_FAILED_KEY,
                    payload);
            System.out.println("[BookingService] PAYMENT_FAILED published for customerId="
                    + customerId + " reason=" + reason);
        } catch (Exception ex) {
            System.err.println("[BookingService] Failed to publish PAYMENT_FAILED: "
                    + ex.getMessage());
        }
    }
}
