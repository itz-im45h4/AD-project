package com.ridelink.farepayment.dto;

import com.ridelink.farepayment.domain.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Data Transfer Objects (DTOs) for Fare &amp; Payment Service.
 *
 * <p>Encapsulates pricing parameters, payment credentials, and transaction receipts
 * across the HTTP API interface.</p>
 */
public final class FareDtos {

    private FareDtos() {
    }

    /** Request payload for generating an upfront estimated fare. */
    public record EstimateRequest(
            @NotBlank String pickup,
            @NotBlank String destination,
            @PositiveOrZero BigDecimal distanceKm,
            @PositiveOrZero BigDecimal durationMinutes
    ) { }

    /** Request payload for calculating and recording the final trip fare. */
    public record FinalizeRequest(
            @NotBlank String rideId,
            @PositiveOrZero BigDecimal distanceKm,
            @PositiveOrZero BigDecimal durationMinutes
    ) { }

    /** Response DTO containing binding fare details for a ride. */
    public record FareResponse(
            String fareId,
            String rideId,
            BigDecimal finalFare,
            String currency,
            PaymentStatus status
    ) { }

    /** Detailed response breakdown of an estimated fare calculation. */
    public record EstimateResponse(
            BigDecimal estimatedFare,
            String currency,
            BigDecimal baseFare,
            BigDecimal perKmRate,
            BigDecimal perMinuteRate
    ) { }

    /** Request payload for initiating payment processing. */
    public record PaymentRequest(
            @NotBlank String rideId,
            @NotBlank String fareId,
            @NotBlank String method
    ) { }

    /** Summary response returned upon payment execution. */
    public record PaymentResponse(
            String paymentId,
            String rideId,
            String fareId,
            PaymentStatus status,
            BigDecimal amount,
            String currency,
            Instant recordedAt
    ) { }

    /** Auditable receipt record representing a completed payment transaction. */
    public record ReceiptResponse(
            String receiptId,
            String rideId,
            String paymentId,
            BigDecimal amount,
            String currency,
            PaymentStatus status,
            Instant issuedAt
    ) { }
}
