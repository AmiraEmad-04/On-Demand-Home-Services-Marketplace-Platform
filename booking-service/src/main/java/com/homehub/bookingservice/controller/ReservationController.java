package com.homehub.bookingservice.controller;

import com.homehub.bookingservice.dto.BookingDtos;
import com.homehub.bookingservice.entity.Reservation;
import com.homehub.bookingservice.service.ReservationOrchestrator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bookings")
public class ReservationController {

    private final ReservationOrchestrator orchestrator;

    public ReservationController(ReservationOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /** POST /api/bookings — customer places a booking */
    @PostMapping
    public ResponseEntity<?> placeBooking(@RequestBody BookingDtos.CreateReservationRequest req) {
        if (req.customerId == null || req.offerId == null) {
            return ResponseEntity.badRequest()
                    .body(new BookingDtos.ErrorBody("customerId and offerId are required"));
        }

        ReservationOrchestrator.BookingResult result =
                orchestrator.placeReservation(req.customerId, req.offerId);

        if (!result.success) {
            String msg = result.errorMessage.toLowerCase();
            HttpStatus status;
            if (msg.contains("insufficient") || msg.contains("balance")) {
                status = HttpStatus.PAYMENT_REQUIRED;       // 402
            } else if (msg.contains("not found")) {
                status = HttpStatus.NOT_FOUND;              // 404
            } else if (msg.contains("unavailable") || msg.contains("status=booked")
                    || msg.contains("only open")) {
                status = HttpStatus.CONFLICT;               // 409
            } else if (msg.contains("deduction failed") || msg.contains("connection error")) {
                status = HttpStatus.BAD_GATEWAY;            // 502 — upstream wallet service problem
            } else {
                status = HttpStatus.INTERNAL_SERVER_ERROR;  // 500
            }
            return ResponseEntity.status(status)
                    .body(new BookingDtos.ErrorBody(result.errorMessage));
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BookingDtos.ReservationView.from(result.reservation));
    }

    /**
     * GET /api/bookings?customerId={id}  → customer history
     * GET /api/bookings?providerId={id}  → provider history
     * GET /api/bookings                  → all (admin)
     */
    @GetMapping
    public ResponseEntity<List<BookingDtos.ReservationView>> listBookings(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long providerId) {

        List<Reservation> records;
        if (customerId != null) {
            records = orchestrator.fetchByCustomer(customerId);
        } else if (providerId != null) {
            records = orchestrator.fetchByProvider(providerId);
        } else {
            records = orchestrator.fetchAll();
        }

        return ResponseEntity.ok(
                records.stream()
                        .map(BookingDtos.ReservationView::from)
                        .collect(Collectors.toList()));
    }

    /** GET /api/bookings/all — explicit all-bookings route for Admin Service */
    @GetMapping("/all")
    public ResponseEntity<List<BookingDtos.ReservationView>> allBookings() {
        return ResponseEntity.ok(
                orchestrator.fetchAll().stream()
                        .map(BookingDtos.ReservationView::from)
                        .collect(Collectors.toList()));
    }

    /** GET /api/bookings/{bookingId} */
    @GetMapping("/{bookingId}")
    public ResponseEntity<?> getBooking(@PathVariable Long bookingId) {
        Optional<Reservation> found = orchestrator.fetchById(bookingId);
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new BookingDtos.ErrorBody("Booking not found: " + bookingId));
        }
        return ResponseEntity.ok(BookingDtos.ReservationView.from(found.get()));
    }
}
