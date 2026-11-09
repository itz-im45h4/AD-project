package com.ridelink.ride.service;

import com.ridelink.ride.domain.Ride;
import com.ridelink.ride.domain.RideStatus;
import com.ridelink.ride.dto.RideDtos.AssignRequest;
import com.ridelink.ride.dto.RideDtos.CompleteRequest;
import com.ridelink.ride.dto.RideDtos.CreateRequest;
import com.ridelink.ride.dto.RideDtos.DriverCandidate;
import com.ridelink.ride.dto.RideDtos.FareResult;
import com.ridelink.ride.dto.RideDtos.Location;
import com.ridelink.ride.dto.RideDtos.RideResponse;
import com.ridelink.ride.exception.ApiException;
import com.ridelink.ride.repository.RideRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Core orchestration service managing the complete Ride lifecycle state machine.
 *
 * <p><strong>Data Ownership &amp; Architectural Boundaries:</strong>
 * <ul>
 *   <li>Ride Management Service owns Ride operational entities and state transitions in MongoDB.</li>
 *   <li>{@code passengerId} and {@code driverUserId} are logical foreign keys referencing Account Service users.</li>
 *   <li>{@code driverId} references the operational driver profile in Driver &amp; Vehicle Service.</li>
 *   <li>Uses synchronous REST calls (with JWT forwarding) for real-time driver availability and fare finalization.</li>
 *   <li>Uses asynchronous AMQP messaging (RabbitMQ) to decouple payment confirmation updates.</li>
 * </ul>
 */
@Service
public class RideService {

    private final RideRepository rides;
    private final RestClient.Builder clients;
    private final String driverUrl;
    private final String fareUrl;

    public RideService(RideRepository rides,
                       RestClient.Builder clients,
                       @Value("${app.services.driver-vehicle.base-url}") String driverUrl,
                       @Value("${app.services.fare-payment.base-url}") String fareUrl) {
        this.rides = rides;
        this.clients = clients;
        this.driverUrl = driverUrl;
        this.fareUrl = fareUrl;
    }

    /**
     * Initializes a new ride booking in the REQUESTED state.
     *
     * <p>Enforces passenger role and verifies that the authenticated user
     * matches the requesting passenger ID.</p>
     *
     * @param auth    Authenticated passenger security context
     * @param request Ride booking details with pickup and destination coordinates
     * @return Created ride resource in REQUESTED status
     */
    public RideResponse create(Authentication auth, CreateRequest request) {
        requireRole(auth, "PASSENGER");
        if (!auth.getName().equals(request.passengerId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Passenger ID must match the authenticated account");
        }

        Ride ride = new Ride();
        ride.setId(UUID.randomUUID().toString());
        ride.setPassengerId(request.passengerId());
        ride.setPickupAddress(request.pickup().address());
        ride.setPickupZone(request.pickup().zone());
        ride.setPickupLatitude(request.pickup().latitude());
        ride.setPickupLongitude(request.pickup().longitude());
        ride.setDestinationAddress(request.destination().address());
        ride.setDestinationZone(request.destination().zone());
        ride.setDestinationLatitude(request.destination().latitude());
        ride.setDestinationLongitude(request.destination().longitude());
        ride.setStatus(RideStatus.REQUESTED);
        ride.setPaymentStatus("PENDING");
        ride.setCreatedAt(Instant.now());

        return response(rides.save(ride));
    }

    /**
     * Transitions ride state from REQUESTED to ASSIGNED by matching with an online driver.
     *
     * <p>Executes a synchronous REST call to Driver &amp; Vehicle Service to query online
     * drivers in the ride's pickup zone. The caller's Bearer token is forwarded in the
     * Authorization header to preserve the user's security context across service boundaries.</p>
     *
     * @param auth    Authenticated passenger security context
     * @param token   Raw Bearer JWT token forwarded to Driver Service
     * @param id      Ride unique identifier
     * @param request Optional request specifying a specific driver ID
     * @return Updated ride resource with driver assignment
     */
    public RideResponse assign(Authentication auth, String token, String id, AssignRequest request) {
        Ride ride = require(id);
        requirePassenger(auth, ride);

        // State Machine validation: only REQUESTED rides can be assigned
        transition(ride, RideStatus.REQUESTED, RideStatus.ASSIGNED);

        // Synchronous interservice call: fetch eligible online drivers in pickup zone
        DriverCandidate[] candidates = loadCandidates(token, ride.getPickupZone());
        if (candidates.length == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "No available driver in pickup zone");
        }

        // Select requested driver or default to the first available candidate
        String requested = request == null ? null : request.driverId();
        DriverCandidate selected;
        if (requested == null || requested.isBlank()) {
            selected = candidates[0];
        } else {
            selected = Arrays.stream(candidates)
                    .filter(c -> requested.equals(c.id()))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Selected driver is not available in pickup zone"));
        }

        if (selected.userId() == null || selected.userId().isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Driver service did not return a userId");
        }

        // Store operational driver ID and user ID for downstream role checks
        ride.setDriverId(selected.id());
        ride.setDriverUserId(selected.userId());

        return response(rides.save(ride));
    }

    /**
     * Transitions ride state from ASSIGNED to ACCEPTED.
     *
     * <p>Only the assigned driver (authenticated via driverUserId) can accept the ride.</p>
     */
    public RideResponse accept(Authentication auth, String id) {
        Ride ride = require(id);
        requireAssignedDriver(auth, ride);
        transition(ride, RideStatus.ASSIGNED, RideStatus.ACCEPTED);
        return response(rides.save(ride));
    }

    /**
     * Transitions ride state from ACCEPTED to IN_PROGRESS upon passenger pickup.
     *
     * <p>Only the assigned driver can start the journey.</p>
     */
    public RideResponse start(Authentication auth, String id) {
        Ride ride = require(id);
        requireAssignedDriver(auth, ride);
        transition(ride, RideStatus.ACCEPTED, RideStatus.IN_PROGRESS);
        return response(rides.save(ride));
    }

    /**
     * Completes an IN_PROGRESS ride and synchronously finalizes the trip fare.
     *
     * <p>Coordinates synchronously with Fare &amp; Payment Service via REST:
     * 1. Validates trip distance and duration.
     * 2. Transitions state to COMPLETED.
     * 3. Forwards Bearer token and calls POST /api/v1/fares/finalize to calculate exact fare.
     * 4. Persists calculated final fare onto the ride record.</p>
     *
     * @param auth    Authenticated driver security context
     * @param token   Raw Bearer JWT token forwarded to Fare Service
     * @param id      Ride unique identifier
     * @param request Distance traveled (km) and trip duration (minutes)
     * @return Completed ride resource containing finalized fare
     */
    public RideResponse complete(Authentication auth, String token, String id, CompleteRequest request) {
        Ride ride = require(id);
        requireAssignedDriver(auth, ride);
        transition(ride, RideStatus.IN_PROGRESS, RideStatus.COMPLETED);

        // Synchronous interservice call: invoke Fare & Payment Service to compute fare
        FareResult fare = clients.clone().baseUrl(fareUrl).build().post()
                .uri("/api/v1/fares/finalize")
                .header(HttpHeaders.AUTHORIZATION, token)
                .body(Map.of(
                        "rideId", ride.getId(),
                        "distanceKm", request.distanceKm(),
                        "durationMinutes", request.durationMinutes()
                ))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "Fare service is unavailable");
                })
                .body(FareResult.class);

        if (fare == null || fare.finalFare() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Fare service did not return a final fare");
        }

        ride.setFinalFare(fare.finalFare());
        return response(rides.save(ride));
    }

    /**
     * Cancels an existing ride booking.
     *
     * <p>Permitted only while the ride is in REQUESTED, ASSIGNED, or ACCEPTED state.
     * Once IN_PROGRESS or COMPLETED, rides cannot be cancelled.</p>
     */
    public RideResponse cancel(Authentication auth, String id) {
        Ride ride = require(id);
        requirePassenger(auth, ride);

        if (!(ride.getStatus() == RideStatus.REQUESTED
                || ride.getStatus() == RideStatus.ASSIGNED
                || ride.getStatus() == RideStatus.ACCEPTED)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ride cannot be cancelled in its current state");
        }

        ride.setStatus(RideStatus.CANCELLED);
        return response(rides.save(ride));
    }

    /**
     * Retrieves ride details, enforcing privacy and access permissions.
     *
     * <p>Only the booking passenger, the assigned driver, or an administrator
     * has authorization to view ride information.</p>
     */
    public RideResponse get(Authentication auth, String id) {
        Ride ride = require(id);
        boolean admin = hasRole(auth, "ADMIN");
        boolean passenger = auth.getName().equals(ride.getPassengerId());
        boolean driver = auth.getName().equals(ride.getDriverUserId());

        if (!admin && !passenger && !driver) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You may only view your own rides");
        }

        return response(ride);
    }

    /**
     * Lists all rides associated with a specific user account.
     *
     * @param auth   Authenticated caller
     * @param userId Target user unique identifier
     * @return List of matching rides
     */
    public List<RideResponse> list(Authentication auth, String userId) {
        if (!auth.getName().equals(userId) && !hasRole(auth, "ADMIN")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You may only view your own ride history");
        }
        return rides.findForUser(userId).stream().map(this::response).toList();
    }

    /**
     * Updates ride payment status upon asynchronous notification from RabbitMQ.
     *
     * <p>Invoked by {@link com.ridelink.ride.messaging.PaymentRecordedListener} when
     * Fare &amp; Payment Service completes payment processing.</p>
     *
     * @param rideId Unique identifier of the ride
     * @param status New payment status (e.g., "PAID", "FAILED")
     */
    public void paymentRecorded(String rideId, String status) {
        rides.findById(rideId).ifPresent(ride -> {
            ride.setPaymentStatus(status);
            rides.save(ride);
        });
    }

    /**
     * Synchronous HTTP REST call to Driver &amp; Vehicle Service to fetch candidate drivers in zone.
     */
    private DriverCandidate[] loadCandidates(String token, String zone) {
        try {
            DriverCandidate[] candidates = clients.clone().baseUrl(driverUrl).build().get()
                    .uri("/api/v1/drivers/available?zone={zone}", zone)
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new ApiException(HttpStatus.BAD_GATEWAY, "Driver service is unavailable");
                    })
                    .body(DriverCandidate[].class);
            return candidates == null ? new DriverCandidate[0] : candidates;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Driver service is unavailable");
        }
    }

    /** Ensures caller holds ROLE_PASSENGER and matches the ride's passengerId. */
    private void requirePassenger(Authentication auth, Ride ride) {
        requireRole(auth, "PASSENGER");
        if (!auth.getName().equals(ride.getPassengerId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the passenger may change this ride");
        }
    }

    /** Ensures caller holds ROLE_DRIVER and matches the ride's assigned driverUserId. */
    private void requireAssignedDriver(Authentication auth, Ride ride) {
        requireRole(auth, "DRIVER");
        if (ride.getDriverUserId() == null || !auth.getName().equals(ride.getDriverUserId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the assigned driver may update this ride");
        }
    }

    /** Enforces role-based authority check. */
    private void requireRole(Authentication auth, String role) {
        if (!hasRole(auth, role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Insufficient role");
        }
    }

    /** Checks whether the authenticated principal has the specified authority. */
    private boolean hasRole(Authentication auth, String role) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    /** Retrieves ride entity or raises 404 NOT_FOUND. */
    private Ride require(String id) {
        return rides.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ride not found"));
    }

    /** Validates finite state machine transitions to protect against invalid state jumps. */
    private void transition(Ride ride, RideStatus from, RideStatus to) {
        if (ride.getStatus() != from) {
            throw new ApiException(HttpStatus.CONFLICT, "Invalid ride status transition");
        }
        ride.setStatus(to);
    }

    /** Maps Ride domain entity to RideResponse DTO. */
    private RideResponse response(Ride ride) {
        return new RideResponse(
                ride.getId(),
                ride.getPassengerId(),
                ride.getDriverId(),
                ride.getDriverUserId(),
                ride.getStatus(),
                new Location(ride.getPickupAddress(), ride.getPickupZone(), ride.getPickupLatitude(), ride.getPickupLongitude()),
                new Location(ride.getDestinationAddress(), ride.getDestinationZone(), ride.getDestinationLatitude(), ride.getDestinationLongitude()),
                ride.getFinalFare(),
                ride.getPaymentStatus(),
                ride.getCreatedAt()
        );
    }
}
