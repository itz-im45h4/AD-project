package com.ridelink.ride.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ridelink.ride.domain.RideStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Data Transfer Objects (DTOs) defining the Ride Management Service API contract.
 *
 * <p>Shields internal MongoDB document structure from external consumers,
 * enforces input validations, and deserializes remote interservice responses.</p>
 */
public final class RideDtos {

    private RideDtos() {
    }

    /** Geographic location and address details for pickup/destination. */
    public record Location(
            @NotBlank String address,
            @NotBlank String zone,
            @NotNull Double latitude,
            @NotNull Double longitude
    ) { }

    /** Request payload for creating a new ride booking. */
    public record CreateRequest(
            @NotBlank String passengerId,
            @NotNull @Valid Location pickup,
            @NotNull @Valid Location destination
    ) { }

    /** Request payload for assigning a driver. */
    public record AssignRequest(
            String driverId
    ) { }

    /** Request payload for completing a trip with actual distance and duration. */
    public record CompleteRequest(
            @PositiveOrZero BigDecimal distanceKm,
            @PositiveOrZero BigDecimal durationMinutes
    ) { }

    /** Request payload for cancelling an existing ride. */
    public record CancelRequest(
            String reason
    ) { }

    /** Public ride representation returned to API clients. */
    public record RideResponse(
            String id,
            String passengerId,
            String driverId,
            String driverUserId,
            RideStatus status,
            Location pickup,
            Location destination,
            BigDecimal finalFare,
            String paymentStatus,
            Instant createdAt
    ) { }

    /** DTO mapping remote response from Driver Service available query. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DriverCandidate(
            String id,
            String userId,
            String serviceZone
    ) { }

    /** DTO mapping remote response from Fare Service finalization endpoint. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FareResult(
            String fareId,
            String rideId,
            BigDecimal finalFare,
            String currency,
            String status
    ) { }
}
