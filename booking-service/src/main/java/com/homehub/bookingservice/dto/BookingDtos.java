package com.homehub.bookingservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.homehub.bookingservice.entity.Reservation;
import java.time.LocalDateTime;

/**
 * All DTO shapes used by the Booking Service.
 */
public class BookingDtos {

    // ── Inbound ────────────────────────────────────────────────
    public static class CreateReservationRequest {
        public Long customerId;
        public Long offerId;
    }

    // ── Outbound: a single booking record ──────────────────────
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReservationView {
        public Long   bookingId;
        public Long   customerId;
        public Long   offerId;
        public Long   providerId;
        public Double chargedAmount;
        public String status;
        public String rejectionReason;
        public LocalDateTime reservedAt;

        public static ReservationView from(Reservation r) {
            ReservationView v  = new ReservationView();
            v.bookingId        = r.getBookingId();
            v.customerId       = r.getCustomerId();
            v.offerId          = r.getOfferId();
            v.providerId       = r.getProviderId();
            v.chargedAmount    = r.getChargedAmount();
            v.status           = r.getStatus().name();
            v.rejectionReason  = r.getRejectionReason();
            v.reservedAt       = r.getReservedAt();
            return v;
        }
    }

    // ── Outbound: simple error body ────────────────────────────
    public static class ErrorBody {
        public String error;
        public ErrorBody(String msg) { this.error = msg; }
    }

    // ── Internal: snapshot of an offer fetched from Offer Svc ──
    public static class OfferSnapshot {
        public Long   offerId;
        public Long   providerId;
        public Double price;
        public String status;   // "OPEN" or "BOOKED"
        public Long   categoryId;
        public String categoryName;
    }

    // ── Internal: wallet balance from Wallet Svc ───────────────
    public static class WalletSnapshot {
        public Long   userId;
        public Double balance;
    }
}
