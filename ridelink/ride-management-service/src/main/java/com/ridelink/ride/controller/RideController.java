package com.ridelink.ride.controller;

import com.ridelink.ride.dto.RideDtos.AssignRequest;
import com.ridelink.ride.dto.RideDtos.CancelRequest;
import com.ridelink.ride.dto.RideDtos.CompleteRequest;
import com.ridelink.ride.dto.RideDtos.CreateRequest;
import com.ridelink.ride.dto.RideDtos.RideResponse;
import com.ridelink.ride.service.RideService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing endpoints for the end-to-end ride lifecycle.
 *
 * <p><strong>Architectural Role:</strong>
 * Acts as the HTTP adapter boundary for ride management. Handles request parameter mapping,
 * input validation, security context injection, and token forwarding to downstream services.</p>
 */
@RestController
@RequestMapping("/api/v1/rides")
public class RideController {

    private final RideService service;

    public RideController(RideService service) {
        this.service = service;
    }

    /**
     * Creates a new ride booking in REQUESTED state.
     *
     * @param auth    Authenticated passenger security context
     * @param request Validated pickup and destination coordinates
     * @return 201 Created with Location header pointing to created ride resource
     */
    @PostMapping
    public ResponseEntity<RideResponse> create(Authentication auth, @Valid @RequestBody CreateRequest request) {
        RideResponse ride = service.create(auth, request);
        return ResponseEntity
                .created(URI.create("/api/v1/rides/" + ride.id()))
                .body(ride);
    }

    /**
     * Assigns a driver to a requested ride.
     *
     * <p>Forwards the caller's Authorization Bearer token to Driver Service
     * to discover available drivers in the pickup zone.</p>
     *
     * @param auth    Authenticated passenger security context
     * @param token   Authorization Bearer token from incoming HTTP header
     * @param id      Target ride ID
     * @param request Optional specific driver selection
     * @return Updated ride resource with driver assignment
     */
    @PostMapping("/{id}/assign")
    public RideResponse assign(Authentication auth,
                               @RequestHeader("Authorization") String token,
                               @PathVariable String id,
                               @RequestBody(required = false) AssignRequest request) {
        return service.assign(auth, token, id, request == null ? new AssignRequest(null) : request);
    }

    /**
     * Driver confirms acceptance of assigned ride.
     *
     * @param auth Authenticated driver security context
     * @param id   Target ride ID
     * @return Updated ride resource in ACCEPTED state
     */
    @PostMapping("/{id}/accept")
    public RideResponse accept(Authentication auth, @PathVariable String id) {
        return service.accept(auth, id);
    }

    /**
     * Driver marks journey started upon passenger pickup.
     *
     * @param auth Authenticated driver security context
     * @param id   Target ride ID
     * @return Updated ride resource in IN_PROGRESS state
     */
    @PostMapping("/{id}/start")
    public RideResponse start(Authentication auth, @PathVariable String id) {
        return service.start(auth, id);
    }

    /**
     * Completes an in-progress ride and calculates final fare synchronously.
     *
     * <p>Forwards the Bearer token to Fare &amp; Payment Service to invoke
     * the fare finalization endpoint.</p>
     *
     * @param auth    Authenticated driver security context
     * @param token   Authorization Bearer token from incoming HTTP header
     * @param id      Target ride ID
     * @param request Final trip distance and duration
     * @return Completed ride resource with computed final fare
     */
    @PostMapping("/{id}/complete")
    public RideResponse complete(Authentication auth,
                                 @RequestHeader("Authorization") String token,
                                 @PathVariable String id,
                                 @Valid @RequestBody CompleteRequest request) {
        return service.complete(auth, token, id, request);
    }

    /**
     * Cancels a ride booking before trip inception.
     *
     * @param auth    Authenticated passenger security context
     * @param id      Target ride ID
     * @param request Optional cancellation reason
     * @return Updated ride resource in CANCELLED state
     */
    @PostMapping("/{id}/cancel")
    public RideResponse cancel(Authentication auth,
                               @PathVariable String id,
                               @RequestBody(required = false) CancelRequest request) {
        return service.cancel(auth, id);
    }

    /**
     * Fetches details of a specific ride.
     *
     * @param auth Authenticated caller (passenger, driver, or admin)
     * @param id   Target ride ID
     * @return Ride details
     */
    @GetMapping("/{id}")
    public RideResponse get(Authentication auth, @PathVariable String id) {
        return service.get(auth, id);
    }

    /**
     * Lists all rides for a given user ID (passenger or driver).
     *
     * @param auth   Authenticated caller
     * @param userId Target user ID
     * @return List of matching rides
     */
    @GetMapping
    public List<RideResponse> list(Authentication auth, @RequestParam String userId) {
        return service.list(auth, userId);
    }
}
