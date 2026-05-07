package com.homehub.offerservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for the Offer Service.
 *
 * Exchanges used:
 *   - bookings.exchange  (fanout) — Offer Service PUBLISHES provider notifications here
 *     when a customer request is matched to a provider's listing.
 *
 * This service does NOT consume from any exchange directly;
 * the Booking Service calls us via REST.
 */
@Configuration
public class RabbitConfig {

    // Exchange names agreed across all services (see API contract)
    public static final String BOOKINGS_EXCHANGE = "bookings.exchange";
    public static final String PAYMENTS_EXCHANGE = "payments.exchange";

    // Queue names this service uses
    public static final String PROVIDER_ALERT_QUEUE = "provider.match.queue";

    @Bean
    public FanoutExchange bookingsExchange() {
        // durable=true so it survives RabbitMQ restarts
        return new FanoutExchange(BOOKINGS_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange paymentsExchange() {
        return new DirectExchange(PAYMENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue providerAlertQueue() {
        return QueueBuilder.durable(PROVIDER_ALERT_QUEUE).build();
    }

    @Bean
    public Binding providerAlertBinding(Queue providerAlertQueue, FanoutExchange bookingsExchange) {
        return BindingBuilder.bind(providerAlertQueue).to(bookingsExchange);
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
