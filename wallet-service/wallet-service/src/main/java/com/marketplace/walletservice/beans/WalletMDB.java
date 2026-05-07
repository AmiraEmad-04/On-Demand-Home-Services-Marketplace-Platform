package com.marketplace.walletservice.beans;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.walletservice.entities.Wallet;
import com.rabbitmq.client.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.ejb.EJB;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import java.util.Map;

/*
 * @MessageDriven is the ideal annotation for RabbitMQ listeners,
 * but JBoss EAP needs extra configuration for native RabbitMQ.
 *
 * Instead we use @Singleton + @Startup which achieves the same goal:
 * - Starts automatically when app deploys
 * - Runs ONE listener for the whole app
 * - Wakes up when a RabbitMQ message arrives
 * - This is the standard approach for RabbitMQ + JBoss EAP
 *
 * WHAT IT DOES:
 * When Booking Service confirms a booking, it publishes a message
 * to RabbitMQ. This bean wakes up, reads the message, and
 * deducts the wallet. If booking fails, it rolls back.
 */
@Singleton
@Startup
public class WalletMDB {

    // RabbitMQ connection settings
    private static final String RABBITMQ_HOST = "localhost";
    private static final String BOOKING_EXCHANGE = "bookings.exchange";
    private static final String WALLET_QUEUE = "wallet.worker.queue";

    private Connection connection;
    private Channel channel;

    @EJB
    private WalletSessionBean walletBean;

    // ObjectMapper converts JSON string → Java Map
    private ObjectMapper objectMapper = new ObjectMapper();

    /*
     * @PostConstruct = runs automatically when JBoss starts this bean.
     * Here we connect to RabbitMQ and start listening.
     */
    @PostConstruct
    public void startListening() {
        try {
            // Step 1: Connect to RabbitMQ
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(RABBITMQ_HOST);
            factory.setUsername("guest");
            factory.setPassword("guest");
            connection = factory.newConnection();
            channel = connection.createChannel();

            // Step 2: Declare the exchange (fanout = sends to all queues)
            channel.exchangeDeclare(BOOKING_EXCHANGE, "fanout", true);

            // Step 3: Declare our queue and bind it to the exchange
            channel.queueDeclare(WALLET_QUEUE, true, false, false, null);
            channel.queueBind(WALLET_QUEUE, BOOKING_EXCHANGE, "");

            System.out.println("WalletMDB: Connected to RabbitMQ, " +
                    "listening on " + WALLET_QUEUE);

            // Step 4: Set up the message handler
            // This runs every time a message arrives
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), "UTF-8");
                System.out.println("WalletMDB received message: " + message);
                processMessage(message);
            };

            // Step 5: Start consuming — autoAck=true means
            // messages are automatically marked as received
            channel.basicConsume(WALLET_QUEUE, true,
                    deliverCallback, consumerTag -> {});

        } catch (Exception e) {
            System.err.println("WalletMDB: Failed to connect to RabbitMQ: "
                    + e.getMessage());
        }
    }

    /*
     * Process the incoming message from Booking Service.
     * Message looks like:
     * {
     *   "eventType": "BOOKING_CONFIRMED",
     *   "bookingId": 5,
     *   "customerId": 1,
     *   "amount": 80.00
     * }
     */
    private void processMessage(String message) {
        try {
            // Convert JSON string to Java Map
            Map<String, Object> event = objectMapper.readValue(
                    message, Map.class);

            String eventType = (String) event.get("eventType");
            Long customerId = Long.valueOf(
                    event.get("customerId").toString());
            Double amount = Double.valueOf(
                    event.get("amount").toString());

            if ("BOOKING_CONFIRMED".equals(eventType)) {
                // Deduct wallet
                Wallet wallet = walletBean.deduct(customerId, amount);
                if (wallet != null) {
                    System.out.println("WalletMDB: Deducted " + amount +
                            " from userId " + customerId +
                            " | New balance: " + wallet.getBalance());
                } else {
                    System.out.println("WalletMDB: Could not deduct — " +
                            "insufficient balance or wallet not found");
                }

            } else if ("PAYMENT_FAILED".equals(eventType)) {
                // Rollback — return money to wallet
                Wallet wallet = walletBean.rollback(customerId, amount);
                if (wallet != null) {
                    System.out.println("WalletMDB: Rolled back " + amount +
                            " to userId " + customerId +
                            " | New balance: " + wallet.getBalance());
                }
            }

        } catch (Exception e) {
            System.err.println("WalletMDB: Error processing message: "
                    + e.getMessage());
        }
    }

    /*
     * @PreDestroy = runs when JBoss shuts down.
     * Always close connections cleanly!
     */
    @PreDestroy
    public void stopListening() {
        try {
            if (channel != null) channel.close();
            if (connection != null) connection.close();
            System.out.println("WalletMDB: RabbitMQ connection closed.");
        } catch (Exception e) {
            System.err.println("WalletMDB: Error closing connection: "
                    + e.getMessage());
        }
    }
}