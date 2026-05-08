package com.homehub.notificationservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for Notification Service.
 *
 * This service CONSUMES from three queues:
 *
 *   1. notification.queue
 *      Bound to: bookings.exchange (fanout)
 *      Receives: BOOKING_CONFIRMED, SERVICE_REQUEST_MATCHED
 *      → Notifies both customer and provider
 *
 *   2. customer.notification.queue
 *      Bound to: payments.exchange (direct, key: "payment.failed")
 *      Receives: PAYMENT_FAILED
 *      → Notifies the customer of the failed payment
 *
 *   3. provider.match.queue
 *      Bound to: bookings.exchange (fanout)
 *      Receives: SERVICE_REQUEST_MATCHED
 *      → Notifies the matched provider (team-of-3 feature)
 *
 * All exchanges and queues are declared idempotently here so this service
 * can start independently.
 */
@Configuration
public class NotificationRabbitConfig {

    // Exchange names — must match booking-service and offer-service
    public static final String BOOKINGS_EXCHANGE         = "bookings.exchange";
    public static final String PAYMENTS_EXCHANGE         = "payments.exchange";
    public static final String PAYMENT_FAILED_KEY        = "payment.failed";

    // Queue names
    public static final String NOTIFICATION_QUEUE        = "notification.queue";
    public static final String CUSTOMER_NOTIFY_QUEUE     = "customer.notification.queue";
    public static final String PROVIDER_MATCH_QUEUE      = "provider.match.queue";

    // ── Exchanges ──────────────────────────────────────────────────────────

    @Bean
    public FanoutExchange bookingsExchange() {
        return new FanoutExchange(BOOKINGS_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange paymentsExchange() {
        return new DirectExchange(PAYMENTS_EXCHANGE, true, false);
    }

    // ── Queues ─────────────────────────────────────────────────────────────

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE).build();
    }

    @Bean
    public Queue customerNotifyQueue() {
        return QueueBuilder.durable(CUSTOMER_NOTIFY_QUEUE).build();
    }

    @Bean
    public Queue providerMatchQueue() {
        return QueueBuilder.durable(PROVIDER_MATCH_QUEUE).build();
    }

    // ── Bindings ───────────────────────────────────────────────────────────

    @Bean
    public Binding notificationBinding(Queue notificationQueue,
                                        FanoutExchange bookingsExchange) {
        return BindingBuilder.bind(notificationQueue).to(bookingsExchange);
    }

    @Bean
    public Binding customerNotifyBinding(Queue customerNotifyQueue,
                                          DirectExchange paymentsExchange) {
        return BindingBuilder.bind(customerNotifyQueue)
                .to(paymentsExchange)
                .with(PAYMENT_FAILED_KEY);
    }

    @Bean
    public Binding providerMatchBinding(Queue providerMatchQueue,
                                         FanoutExchange bookingsExchange) {
        return BindingBuilder.bind(providerMatchQueue).to(bookingsExchange);
    }

    // ── Converter & Template ───────────────────────────────────────────────

    @Bean
    public Jackson2JsonMessageConverter jsonConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf) {
        RabbitTemplate tpl = new RabbitTemplate(cf);
        tpl.setMessageConverter(jsonConverter());
        return tpl;
    }
}
