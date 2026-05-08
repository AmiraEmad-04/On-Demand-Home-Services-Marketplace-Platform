package com.homehub.adminservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for Admin Service.
 *
 * Admin Service consumes from:
 *   - admin.notification.queue  (bound to payments.exchange with key "payment.failed")
 *     → receives PAYMENT_FAILED alerts
 *
 * Both exchanges and the queue are already declared by booking-service,
 * but we re-declare them here (idempotent) so Admin Service can start
 * independently without requiring booking-service to be up first.
 */
@Configuration
public class AdminRabbitConfig {

    public static final String PAYMENTS_EXCHANGE     = "payments.exchange";
    public static final String PAYMENT_FAILED_KEY    = "payment.failed";
    public static final String ADMIN_NOTIFY_QUEUE    = "admin.notification.queue";

    @Bean
    public DirectExchange paymentsExchange() {
        return new DirectExchange(PAYMENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue adminNotifyQueue() {
        return QueueBuilder.durable(ADMIN_NOTIFY_QUEUE).build();
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
