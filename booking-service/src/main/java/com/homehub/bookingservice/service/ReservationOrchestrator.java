package com.homehub.bookingservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homehub.bookingservice.dto.BookingDtos;
import com.homehub.bookingservice.entity.Reservation;
import com.homehub.bookingservice.messaging.ReservationEventPublisher;
import com.homehub.bookingservice.repository.ReservationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class ReservationOrchestrator {

    private final ReservationRepository reservationRepo;
    private final ReservationEventPublisher eventPublisher;
    private final RestTemplate http;
    private final ObjectMapper mapper;

    @Value("${services.offer-service.base-url}")
    private String offerServiceUrl;

    @Value("${services.wallet-service.base-url}")
    private String walletServiceUrl;

    public ReservationOrchestrator(ReservationRepository reservationRepo,
                                   ReservationEventPublisher eventPublisher) {
        this.reservationRepo = reservationRepo;
        this.eventPublisher  = eventPublisher;
        this.http            = new RestTemplate();
        this.mapper          = new ObjectMapper();
    }

    public BookingResult placeReservation(Long customerId, Long offerId) {

        // Step 1: Fetch and validate the listing
        BookingDtos.OfferSnapshot offer = fetchOfferDetails(offerId);
        if (offer == null) {
            eventPublisher.emitPaymentFailed(customerId, offerId, 0.0, "OFFER_NOT_FOUND");
            return BookingResult.failed("Listing not found: " + offerId);
        }
        if (offer.status == null || !"OPEN".equalsIgnoreCase(offer.status)) {
            double price = offer.price != null ? offer.price : 0.0;
            eventPublisher.emitPaymentFailed(customerId, offerId, price, "OFFER_UNAVAILABLE");
            String currentStatus = offer.status != null ? offer.status : "UNKNOWN";
            return BookingResult.failed("Offer unavailable (status=" + currentStatus
                    + "). Only OPEN offers can be booked.");
        }

        // Step 2: Check wallet balance
        BookingDtos.WalletSnapshot wallet = fetchWalletBalance(customerId);
        if (wallet == null) {
            eventPublisher.emitPaymentFailed(customerId, offerId, offer.price, "WALLET_NOT_FOUND");
            return BookingResult.failed("Wallet not found for customer: " + customerId);
        }
        if (wallet.balance < offer.price) {
            eventPublisher.emitPaymentFailed(customerId, offerId, offer.price, "INSUFFICIENT_BALANCE");
            return BookingResult.failed(
                    String.format("Insufficient balance. Need %.2f, have %.2f",
                            offer.price, wallet.balance));
        }

        // Step 3: Deduct payment
        String deductError = deductFromWallet(customerId, offer.price);
        if (deductError != null) {
            eventPublisher.emitPaymentFailed(customerId, offerId, offer.price, "DEDUCTION_FAILED");
            return BookingResult.failed("Payment deduction failed: " + deductError);
        }

        // Step 4: Save reservation record
        Reservation draft = new Reservation();
        draft.setCustomerId(customerId);
        draft.setOfferId(offerId);
        draft.setProviderId(offer.providerId);
        draft.setChargedAmount(offer.price);
        draft.setStatus(Reservation.ReservationStatus.CONFIRMED);
        Reservation saved = reservationRepo.save(draft);

        // Step 5: Mark listing as BOOKED in Offer Service
        boolean markedBooked = markOfferBooked(offerId, customerId, saved.getBookingId());
        if (!markedBooked) {
            rollbackWallet(customerId, offer.price);
            reservationRepo.delete(saved);
            eventPublisher.emitPaymentFailed(customerId, offerId, offer.price, "OFFER_STATE_UPDATE_FAILED");
            return BookingResult.failed("Could not finalise booking — payment refunded");
        }

        // Step 6: Publish success event
        eventPublisher.emitBookingConfirmed(saved);

        return BookingResult.ok(saved);
    }

    public List<Reservation> fetchAll() {
        return reservationRepo.findAllByOrderByReservedAtDesc();
    }

    public List<Reservation> fetchByCustomer(Long customerId) {
        return reservationRepo.findByCustomerId(customerId);
    }

    public List<Reservation> fetchByProvider(Long providerId) {
        return reservationRepo.findByProviderId(providerId);
    }

    public Optional<Reservation> fetchById(Long bookingId) {
        return reservationRepo.findById(bookingId);
    }

    // ── Private REST helpers ──────────────────────────────

    private BookingDtos.OfferSnapshot fetchOfferDetails(Long offerId) {
        try {
            String url = offerServiceUrl + "/api/offers/" + offerId;
            System.out.println("[BookingService] Calling: GET " + url);
            ResponseEntity<BookingDtos.OfferSnapshot> resp =
                    http.getForEntity(url, BookingDtos.OfferSnapshot.class);
            System.out.println("[BookingService] Offer response: " + resp.getStatusCode()
                    + " status=" + (resp.getBody() != null ? resp.getBody().status : "null"));
            return resp.getStatusCode().is2xxSuccessful() ? resp.getBody() : null;
        } catch (Exception ex) {
            System.err.println("[BookingService] fetchOfferDetails failed: " + ex.getMessage());
            return null;
        }
    }

    private BookingDtos.WalletSnapshot fetchWalletBalance(Long customerId) {
        try {
            String url = walletServiceUrl + "/wallet-service/api/wallet/" + customerId + "/balance";
            System.out.println("[BookingService] Calling: GET " + url);
            ResponseEntity<BookingDtos.WalletSnapshot> resp =
                    http.getForEntity(url, BookingDtos.WalletSnapshot.class);
            System.out.println("[BookingService] Wallet balance response: " + resp.getStatusCode()
                    + " balance=" + (resp.getBody() != null ? resp.getBody().balance : "null"));
            return resp.getStatusCode().is2xxSuccessful() ? resp.getBody() : null;
        } catch (Exception ex) {
            System.err.println("[BookingService] fetchWalletBalance failed: " + ex.getMessage());
            return null;
        }
    }

    /**
     * Returns null on success, or an error description string on failure.
     */
    private String deductFromWallet(Long customerId, Double amount) {
        String url = walletServiceUrl + "/wallet-service/api/wallet/" + customerId + "/deduct";
        System.out.println("[BookingService] Calling: PATCH " + url + " amount=" + amount);
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("amount", amount);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

            ResponseEntity<Map> resp = http.exchange(url, HttpMethod.PATCH, req, Map.class);
            System.out.println("[BookingService] Deduct response: " + resp.getStatusCode());
            if (resp.getStatusCode().is2xxSuccessful()) {
                return null; // success
            }
            return "HTTP " + resp.getStatusCode() + " from wallet service";
        } catch (HttpClientErrorException ex) {
            System.err.println("[BookingService] deductFromWallet 4xx: "
                    + ex.getStatusCode() + " body: " + ex.getResponseBodyAsString());
            return "HTTP " + ex.getStatusCode() + ": " + ex.getResponseBodyAsString();
        } catch (HttpServerErrorException ex) {
            System.err.println("[BookingService] deductFromWallet 5xx: "
                    + ex.getStatusCode() + " body: " + ex.getResponseBodyAsString());
            return "HTTP " + ex.getStatusCode() + " from wallet service";
        } catch (Exception ex) {
            System.err.println("[BookingService] deductFromWallet connection error: " + ex.getMessage());
            return "Connection error to wallet service: " + ex.getMessage();
        }
    }

    private boolean markOfferBooked(Long offerId, Long customerId, Long bookingId) {
        try {
            String url = offerServiceUrl + "/api/offers/" + offerId + "/complete";
            System.out.println("[BookingService] Calling: PATCH " + url);
            Map<String, Object> body = new HashMap<>();
            body.put("customerId", customerId);
            body.put("bookingId",  bookingId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

            ResponseEntity<Map> resp = http.exchange(url, HttpMethod.PATCH, req, Map.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception ex) {
            System.err.println("[BookingService] markOfferBooked failed: " + ex.getMessage());
            return false;
        }
    }

    private void rollbackWallet(Long customerId, Double amount) {
        try {
            String url = walletServiceUrl + "/wallet-service/api/wallet/" + customerId + "/rollback";
            System.out.println("[BookingService] Calling: PATCH " + url + " (rollback)");
            Map<String, Object> body = new HashMap<>();
            body.put("amount", amount);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);

            http.exchange(url, HttpMethod.PATCH, req, Map.class);
            System.out.println("[BookingService] Wallet rolled back for customerId=" + customerId);
        } catch (Exception ex) {
            System.err.println("[BookingService] Rollback failed: " + ex.getMessage());
        }
    }

    // ── Result wrapper ────────────────────────────────────

    public static class BookingResult {
        public final boolean     success;
        public final Reservation reservation;
        public final String      errorMessage;

        private BookingResult(boolean success, Reservation reservation, String errorMessage) {
            this.success      = success;
            this.reservation  = reservation;
            this.errorMessage = errorMessage;
        }

        public static BookingResult ok(Reservation r)   { return new BookingResult(true,  r,    null); }
        public static BookingResult failed(String msg)  { return new BookingResult(false, null, msg);  }
    }
}
