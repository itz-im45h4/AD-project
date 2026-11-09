package com.ridelink.drivervehicle.service;

import com.ridelink.drivervehicle.domain.Availability;
import com.ridelink.drivervehicle.domain.Driver;
import com.ridelink.drivervehicle.domain.Vehicle;
import com.ridelink.drivervehicle.dto.DriverDtos.AvailabilityRequest;
import com.ridelink.drivervehicle.dto.DriverDtos.DriverResponse;
import com.ridelink.drivervehicle.dto.DriverDtos.LocationRequest;
import com.ridelink.drivervehicle.dto.DriverDtos.ProfileRequest;
import com.ridelink.drivervehicle.dto.DriverDtos.VehicleRequest;
import com.ridelink.drivervehicle.dto.DriverDtos.VehicleResponse;
import com.ridelink.drivervehicle.exception.ApiException;
import com.ridelink.drivervehicle.repository.DriverRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Service encapsulating driver operational profiles, vehicle registration, and availability status.
 *
 * <p><strong>Data Ownership & Boundaries:</strong>
 * Driver &amp; Vehicle Service owns operational driver documents and vehicle specifications in MongoDB.
 * The {@code userId} field is an opaque foreign reference to an Account entity owned by Account Service.
 * This service does not duplicate user credentials or personal profile data, preserving strict bounded context.</p>
 */
@Service
public class DriverService {

    private final DriverRepository drivers;

    public DriverService(DriverRepository drivers) {
        this.drivers = drivers;
    }

    /**
     * Registers a new driver operational profile with associated vehicle details.
     *
     * <p>Enforces three business invariants:
     * 1. The authenticated caller must be the driver identified by {@code userId}.
     * 2. A driver can only have one active driver profile (1:1 user-to-driver mapping).
     * 3. Driver licenses and vehicle license plates must be globally unique across the fleet.</p>
     *
     * @param authentication Authenticated security context containing driver identity and roles
     * @param request        Profile request with license, vehicle specs, and primary service zone
     * @return Created driver operational response DTO
     */
    public DriverResponse create(Authentication authentication, ProfileRequest request) {
        // Enforce principal identity and DRIVER role ownership
        requireDriver(authentication, request.userId());

        // Check uniqueness constraints to prevent duplicate fleet registration
        if (drivers.existsByUserId(request.userId())
                || drivers.existsByLicenseNumber(request.licenseNumber())
                || drivers.existsByVehiclePlateNumber(request.vehicle().plateNumber())) {
            throw new ApiException(HttpStatus.CONFLICT, "Driver, license, or vehicle already exists");
        }

        // Initialize driver profile with default OFFLINE status
        Driver driver = new Driver();
        driver.setId(UUID.randomUUID().toString());
        driver.setUserId(request.userId());
        driver.setLicenseNumber(request.licenseNumber());
        driver.setServiceZone(request.serviceZone());
        driver.setAvailability(Availability.OFFLINE);
        driver.setCreatedAt(Instant.now());
        driver.setVehicle(toVehicle(request.vehicle()));

        return toResponse(drivers.save(driver));
    }

    /**
     * Updates driver operational status (ONLINE, OFFLINE, BUSY).
     *
     * <p>Only the driver owning the profile may modify their availability.</p>
     *
     * @param authentication Authenticated caller
     * @param driverId       Driver entity ID
     * @param request        Target availability status
     * @return Updated driver profile
     */
    public DriverResponse availability(Authentication authentication, String driverId, AvailabilityRequest request) {
        Driver driver = require(driverId);
        requireDriver(authentication, driver.getUserId());

        driver.setAvailability(request.availability());
        return toResponse(drivers.save(driver));
    }

    /**
     * Updates driver geographic coordinates and current operating zone.
     *
     * <p>Enforces location telemetry updates only by the authenticated driver.</p>
     *
     * @param authentication Authenticated caller
     * @param driverId       Driver entity ID
     * @param request        New latitude, longitude, and active operational zone
     * @return Updated driver profile
     */
    public DriverResponse location(Authentication authentication, String driverId, LocationRequest request) {
        Driver driver = require(driverId);
        requireDriver(authentication, driver.getUserId());

        driver.setLatitude(request.latitude());
        driver.setLongitude(request.longitude());
        driver.setServiceZone(request.zone());
        return toResponse(drivers.save(driver));
    }

    /**
     * Queries available drivers within a specific geographic service zone.
     *
     * <p>Invoked synchronously by Ride Management Service via REST client
     * during ride matching and driver assignment.</p>
     *
     * @param zone Operational service zone (e.g., "central", "north")
     * @return List of online drivers ready for ride allocation
     */
    public List<DriverResponse> available(String zone) {
        return drivers.findByAvailabilityAndServiceZone(Availability.ONLINE, zone)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Retrieves a driver operational profile by unique identifier.
     *
     * @param id Driver entity identifier
     * @return Driver operational response DTO
     * @throws ApiException with 404 NOT_FOUND if no driver matches the ID
     */
    public DriverResponse get(String id) {
        return toResponse(require(id));
    }

    /**
     * Access control verification: ensures caller principal matches target userId and holds ROLE_DRIVER.
     */
    private void requireDriver(Authentication authentication, String userId) {
        if (authentication == null
                || !authentication.getName().equals(userId)
                || authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_DRIVER"))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the owning driver may change this profile");
        }
    }

    /**
     * Retrieves driver document or raises 404 NOT_FOUND.
     */
    private Driver require(String id) {
        return drivers.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Driver not found"));
    }

    /**
     * Maps VehicleRequest DTO to embedded Vehicle domain object.
     */
    private Vehicle toVehicle(VehicleRequest request) {
        Vehicle vehicle = new Vehicle();
        vehicle.setMake(request.make());
        vehicle.setModel(request.model());
        vehicle.setPlateNumber(request.plateNumber());
        vehicle.setCapacity(request.capacity());
        return vehicle;
    }

    /**
     * Maps Driver domain entity and embedded Vehicle to public DriverResponse DTO.
     */
    private DriverResponse toResponse(Driver driver) {
        Vehicle vehicle = driver.getVehicle();
        return new DriverResponse(
                driver.getId(),
                driver.getUserId(),
                driver.getLicenseNumber(),
                driver.getAvailability(),
                driver.getServiceZone(),
                driver.getLatitude(),
                driver.getLongitude(),
                new VehicleResponse(
                        vehicle.getMake(),
                        vehicle.getModel(),
                        vehicle.getPlateNumber(),
                        vehicle.getCapacity()
                ),
                driver.getCreatedAt()
        );
    }
}
