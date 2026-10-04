package com.ridelink.drivervehicle.dto;

import com.ridelink.drivervehicle.domain.Availability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Data Transfer Objects (DTOs) for the Driver &amp; Vehicle Service API boundary.
 *
 * <p>Separating API DTOs from internal MongoDB domain entities protects the internal
 * schema from external changes and enforces strict request validation constraints.</p>
 */
public final class DriverDtos {

    private DriverDtos() {
    }

    /** Request payload for vehicle specifications. */
    public record VehicleRequest(
            @NotBlank String make,
            @NotBlank String model,
            @NotBlank String plateNumber,
            @Min(1) @Max(12) int capacity
    ) { }

    /** Request payload for driver profile registration. */
    public record ProfileRequest(
            @NotBlank String userId,
            @NotBlank String licenseNumber,
            @NotNull @Valid VehicleRequest vehicle,
            @NotBlank String serviceZone
    ) { }

    /** Request payload for updating operational availability status. */
    public record AvailabilityRequest(
            @NotNull Availability availability
    ) { }

    /** Request payload for GPS coordinates and active service zone. */
    public record LocationRequest(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @NotBlank String zone
    ) { }

    /** Response DTO containing public vehicle details. */
    public record VehicleResponse(
            String make,
            String model,
            String plateNumber,
            int capacity
    ) { }

    /** Response DTO containing public driver profile and operational status. */
    public record DriverResponse(
            String id,
            String userId,
            String licenseNumber,
            Availability availability,
            String serviceZone,
            Double latitude,
            Double longitude,
            VehicleResponse vehicle,
            Instant createdAt
    ) { }
}
