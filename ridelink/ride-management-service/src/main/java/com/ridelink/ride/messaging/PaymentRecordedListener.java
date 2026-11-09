package com.ridelink.ride.messaging;

import com.ridelink.ride.config.RabbitConfig;
import com.ridelink.ride.service.RideService;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Asynchronous RabbitMQ message listener consuming payment events.
 *
 * <p><strong>Event-Driven Decoupling:</strong>
 * Implements the choreography-based saga pattern for asynchronous settlement. When Fare &amp; Payment
 * Service successfully processes a payment, it publishes a {@code PaymentRecorded} event to RabbitMQ.
 * This listener consumes the event in the background and updates the ride's payment status,
 * ensuring eventual consistency without requiring Fare Service to block or call Ride Service synchronously.</p>
 */
@Component
public class PaymentRecordedListener {

    private final RideService rides;

    public PaymentRecordedListener(RideService rides) {
        this.rides = rides;
    }

    /**
     * Consumes payment recorded events from the durable RabbitMQ queue.
     *
     * @param event Deserialized payment event payload containing rideId and status
     */
    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void paymentRecorded(PaymentRecorded event) {
        if (event != null && event.rideId() != null) {
            rides.paymentRecorded(event.rideId(), event.status());
        }
    }

    /**
     * DTO mapping the asynchronous AMQP event emitted by Fare &amp; Payment Service.
     */
    public record PaymentRecorded(
            String eventId,
            String eventType,
            Instant occurredAt,
            String rideId,
            String paymentId,
            String status,
            BigDecimal amount,
            String currency
    ) { }
}
