package com.ridelink.farepayment.service;

import com.ridelink.farepayment.config.RabbitConfig;
import com.ridelink.farepayment.domain.Fare;
import com.ridelink.farepayment.domain.Payment;
import com.ridelink.farepayment.domain.PaymentStatus;
import com.ridelink.farepayment.domain.Receipt;
import com.ridelink.farepayment.dto.FareDtos.EstimateRequest;
import com.ridelink.farepayment.dto.FareDtos.EstimateResponse;
import com.ridelink.farepayment.dto.FareDtos.FareResponse;
import com.ridelink.farepayment.dto.FareDtos.FinalizeRequest;
import com.ridelink.farepayment.dto.FareDtos.PaymentRequest;
import com.ridelink.farepayment.dto.FareDtos.PaymentResponse;
import com.ridelink.farepayment.dto.FareDtos.ReceiptResponse;
import com.ridelink.farepayment.exception.ApiException;
import com.ridelink.farepayment.messaging.PaymentRecorded;
import com.ridelink.farepayment.repository.FareRepository;
import com.ridelink.farepayment.repository.PaymentRepository;
import com.ridelink.farepayment.repository.ReceiptRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Service managing pricing calculations, fare finalization, simulated payment settlement,
 * and immutable payment receipt auditing.
 *
 * <p><strong>Data Ownership &amp; Architectural Boundaries:</strong>
 * <ul>
 *   <li>Fare &amp; Payment Service owns financial calculations and ledger records in MongoDB (fares, payments, receipts).</li>
 *   <li>Uses exact {@link BigDecimal} arithmetic (with 2 decimal places and HALF_UP rounding) to avoid binary floating-point errors.</li>
 *   <li>Exposes synchronous REST endpoints for upfront fare estimation and trip completion fare finalization.</li>
 *   <li>Publishes asynchronous AMQP events to RabbitMQ upon payment recording, enabling decoupled status updates in Ride Management.</li>
 * </ul>
 */
@Service
public class FareService {

    /** Base pickup fee in LKR. */
    static final BigDecimal BASE = new BigDecimal("200.00");

    /** Variable distance rate in LKR per kilometer. */
    static final BigDecimal PER_KM = new BigDecimal("50.00");

    /** Variable duration rate in LKR per minute. */
    static final BigDecimal PER_MIN = new BigDecimal("10.00");

    private final FareRepository fares;
    private final PaymentRepository payments;
    private final ReceiptRepository receipts;
    private final RabbitTemplate rabbit;

    public FareService(FareRepository fares,
                       PaymentRepository payments,
                       ReceiptRepository receipts,
                       RabbitTemplate rabbit) {
        this.fares = fares;
        this.payments = payments;
        this.receipts = receipts;
        this.rabbit = rabbit;
    }

    /**
     * Calculates an upfront fare estimate based on projected distance and duration.
     *
     * <p>Formula: {@code Base (200.00) + (Distance * 50.00) + (Duration * 10.00)}</p>
     *
     * @param request Estimated distance in kilometers and duration in minutes
     * @return Transparent estimate response detailing the total and rate components
     */
    public EstimateResponse estimate(EstimateRequest request) {
        BigDecimal estimatedAmount = calculate(request.distanceKm(), request.durationMinutes());
        return new EstimateResponse(
                estimatedAmount,
                "LKR",
                BASE,
                PER_KM,
                PER_MIN
        );
    }

    /**
     * Finalizes and records the binding fare for a completed ride.
     *
     * <p>Invoked synchronously by Ride Management Service when a ride completes.
     * Idempotently creates or updates the fare document for the specified {@code rideId}.</p>
     *
     * @param request Ride ID with final recorded distance and duration metrics
     * @return Finalized fare record in PENDING payment status
     */
    public FareResponse finalize(FinalizeRequest request) {
        BigDecimal amount = calculate(request.distanceKm(), request.durationMinutes());

        // Idempotent upsert: find existing fare for ride or create new entity
        Fare fare = fares.findByRideId(request.rideId()).orElseGet(() -> {
            Fare created = new Fare();
            created.setId(UUID.randomUUID().toString());
            created.setRideId(request.rideId());
            created.setCreatedAt(Instant.now());
            return created;
        });

        fare.setAmount(amount);
        fares.save(fare);

        return new FareResponse(
                fare.getId(),
                fare.getRideId(),
                fare.getAmount(),
                "LKR",
                PaymentStatus.PENDING
        );
    }

    /**
     * Processes simulated payment for a finalized fare, generates a receipt,
     * and broadcasts an asynchronous payment event via RabbitMQ.
     *
     * <p>Payment simulation: method "FAIL" simulates gateway card decline;
     * any other method triggers SUCCESS.</p>
     *
     * @param request Payment details including rideId, fareId, and payment method
     * @return Recorded payment transaction summary
     */
    public PaymentResponse payment(PaymentRequest request) {
        // Verify fare exists and belongs to the requested ride
        Fare fare = fares.findById(request.fareId())
                .filter(existing -> existing.getRideId().equals(request.rideId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Fare not found for this ride"));

        // Idempotent payment processing: retrieve existing payment or record new one
        Payment payment = payments.findByRideId(request.rideId()).orElseGet(() -> persistPayment(request, fare));

        // Publish event to RabbitMQ topic exchange to notify interested microservices (Ride Management)
        publish(payment);

        return response(payment);
    }

    /**
     * Retrieves the audit receipt for a processed payment.
     *
     * @param rideId Unique identifier of the ride
     * @return Issued receipt details
     */
    public ReceiptResponse receipt(String rideId) {
        Receipt receipt = receipts.findByRideId(rideId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Receipt not found"));

        return new ReceiptResponse(
                receipt.getId(),
                receipt.getRideId(),
                receipt.getPaymentId(),
                receipt.getAmount(),
                "LKR",
                receipt.getStatus(),
                receipt.getIssuedAt()
        );
    }

    /**
     * Retrieves a finalized fare record by ride ID.
     *
     * @param rideId Unique identifier of the ride
     * @return Finalized fare response DTO
     * @throws ApiException with 404 NOT_FOUND if no fare exists for this ride
     */
    public FareResponse getFare(String rideId) {
        Fare fare = fares.findByRideId(rideId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Fare not found for this ride"));

        return new FareResponse(
                fare.getId(),
                fare.getRideId(),
                fare.getAmount(),
                "LKR",
                PaymentStatus.PENDING
        );
    }

    /**
     * Computes total fare using exact financial arithmetic.
     * Formula: {@code Base + (Distance * RatePerKm) + (Duration * RatePerMin)}
     * Scaled to 2 decimal places with HALF_UP rounding.
     */
    static BigDecimal calculate(BigDecimal distanceKm, BigDecimal durationMinutes) {
        return BASE
                .add(distanceKm.multiply(PER_KM))
                .add(durationMinutes.multiply(PER_MIN))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Persists new Payment transaction and generates matching Receipt audit record.
     */
    private Payment persistPayment(PaymentRequest request, Fare fare) {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID().toString());
        payment.setRideId(request.rideId());
        payment.setFareId(fare.getId());
        payment.setMethod(request.method());
        payment.setAmount(fare.getAmount());
        payment.setStatus("FAIL".equalsIgnoreCase(request.method()) ? PaymentStatus.FAILED : PaymentStatus.SUCCESS);
        payment.setRecordedAt(Instant.now());
        payments.save(payment);

        // Create matching receipt document
        Receipt receipt = new Receipt();
        receipt.setId("receipt-" + payment.getId());
        receipt.setRideId(payment.getRideId());
        receipt.setPaymentId(payment.getId());
        receipt.setAmount(payment.getAmount());
        receipt.setStatus(payment.getStatus());
        receipt.setIssuedAt(payment.getRecordedAt());
        receipts.save(receipt);

        return payment;
    }

    /**
     * Broadcasts asynchronous PaymentRecorded integration event to RabbitMQ.
     */
    private void publish(Payment payment) {
        rabbit.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.ROUTING_KEY,
                new PaymentRecorded(
                        UUID.randomUUID().toString(),
                        "PaymentRecorded",
                        payment.getRecordedAt(),
                        payment.getRideId(),
                        payment.getId(),
                        payment.getStatus().name(),
                        payment.getAmount(),
                        "LKR"
                )
        );
    }

    /** Maps internal Payment domain entity to public PaymentResponse DTO. */
    private PaymentResponse response(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getRideId(),
                payment.getFareId(),
                payment.getStatus(),
                payment.getAmount(),
                "LKR",
                payment.getRecordedAt()
        );
    }
}
