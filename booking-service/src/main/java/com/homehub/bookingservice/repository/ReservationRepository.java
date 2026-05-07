package com.homehub.bookingservice.repository;

import com.homehub.bookingservice.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByCustomerId(Long customerId);

    List<Reservation> findByProviderId(Long providerId);

    /** Admin: all bookings regardless of who */
    List<Reservation> findAllByOrderByReservedAtDesc();
}
