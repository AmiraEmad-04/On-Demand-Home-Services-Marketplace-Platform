package com.homehub.notificationservice.repository;

import com.homehub.notificationservice.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** All notifications for a specific user, newest first. */
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** All notifications, newest first (admin view). */
    List<Notification> findAllByOrderByCreatedAtDesc();
}
