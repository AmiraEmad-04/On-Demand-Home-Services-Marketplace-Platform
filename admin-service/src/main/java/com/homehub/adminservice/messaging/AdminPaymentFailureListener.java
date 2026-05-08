package com.homehub.adminservice.messaging;

import com.homehub.adminservice.config.AdminRabbitConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Listens on admin.notification.queue for PAYMENT_FAILED events.
 *
 * This fulfils the team-of-3 requirement:
 *   "Admins should be notified in case of payment failure via payments.exchange direct exchange"
 *
 * Alerts are stored in-memory and exposed via AdminController GET /api/admin/alerts.
 */
@Component
public class AdminPaymentFailureListener {

    /** In-memory store of payment failure alerts received since startup. */
    private final List<Map<String, Object>> alerts =
            Collections.synchronizedList(new ArrayList<>());

    @RabbitListener(queues = AdminRabbitConfig.ADMIN_NOTIFY_QUEUE)
    public void onPaymentFailed(Map<String, Object> event) {
        System.out.println("[AdminService] PAYMENT_FAILED alert received: " + event);
        alerts.add(event);
    }

    /** Returns a snapshot of all received payment failure alerts. */
    public List<Map<String, Object>> getAlerts() {
        return List.copyOf(alerts);
    }
}
