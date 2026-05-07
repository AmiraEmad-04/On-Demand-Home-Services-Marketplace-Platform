package com.homehub.bookingservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for Booking Service.
 *
 * This service is the PRIMARY PUBLISHER for both exchanges.
 *
 * bookings.exchange (fanout)
 *   Publishes:  BookingConfirmed — when payment succeeds and listing is marked BOOKED
 *   Consumers:  Notification Service (M3), Wallet MDB (M1)
 *
 * payments.exchange (direct)
 *   Publishes:  PaymentFailed — when balance is short or listing is unavailable
 *   Routing key: "payment.failed"
 *   Consumers:  Notification Service (customer queue + admin queue)
 */
@Configuration
public class BookingRabbitConfig {

    // Exchange names — must match across all services (see API contract)
    public static final String BOOKINGS_EXCHANGE    = "bookings.exchange";
    public static final String PAYMENTS_EXCHANGE    = "payments.exchange";
    public static final String PAYMENT_FAILED_KEY   = "payment.failed";

    // Queues declared here so they exist when RabbitMQ starts
    public static final String BOOKING_CONFIRM_QUEUE    = "booking.confirmation.queue";
    public static final String NOTIFICATION_QUEUE        = "notification.queue";
    public static final String CUSTOMER_NOTIFY_QUEUE     = "customer.notification.queue";
    public static final String ADMIN_NOTIFY_QUEUE        = "admin.notification.queue";

    @Bean
    public FanoutExchange bookingsExchange() {
        return new FanoutExchange(BOOKINGS_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange paymentsExchange() {
        return new DirectExchange(PAYMENTS_EXCHANGE, true, false);
    }

    // Queues for bookings.exchange (fanout)
    @Bean
    public Queue bookingConfirmQueue() {
        return QueueBuilder.durable(BOOKING_CONFIRM_QUEUE).build();
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE).build();
    }

    @Bean
    public Binding bookingConfirmBinding(Queue bookingConfirmQueue,
                                         FanoutExchange bookingsExchange) {
        return BindingBuilder.bind(bookingConfirmQueue).to(bookingsExchange);
    }

    @Bean
    public Binding notificationBinding(Queue notificationQueue,
                                        FanoutExchange bookingsExchange) {
        return BindingBuilder.bind(notificationQueue).to(bookingsExchange);
    }

    // Queues for payments.exchange (direct)
    @Bean
    public Queue customerNotifyQueue() {
        return QueueBuilder.durable(CUSTOMER_NOTIFY_QUEUE).build();
    }

    @Bean
    public Queue adminNotifyQueue() {
        return QueueBuilder.durable(ADMIN_NOTIFY_QUEUE).build();
    }

    @Bean
    public Binding customerNotifyBinding(Queue customerNotifyQueue,
                                          DirectExchange paymentsExchange) {
        return BindingBuilder.bind(customerNotifyQueue)
                .to(paymentsExchange)
                .with(PAYMENT_FAILED_KEY);
    }

    @Bean
    public Binding adminNotifyBinding(Queue adminNotifyQueue,
                                       DirectExchange paymentsExchange) {
        return BindingBuilder.bind(adminNotifyQueue)
                .to(paymentsExchange)
                .with(PAYMENT_FAILED_KEY);
    }

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
