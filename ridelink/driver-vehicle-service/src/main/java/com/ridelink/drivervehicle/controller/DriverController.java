package com.ridelink.drivervehicle.controller;

import com.ridelink.drivervehicle.dto.DriverDtos.AvailabilityRequest;
import com.ridelink.drivervehicle.dto.DriverDtos.DriverResponse;
import com.ridelink.drivervehicle.dto.DriverDtos.LocationRequest;
import com.ridelink.drivervehicle.dto.DriverDtos.ProfileRequest;
import com.ridelink.drivervehicle.service.DriverService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Driver &amp; Vehicle operational lifecycle.
 *
 * <p><strong>Architectural Role:</strong>
 * Acts as the HTTP adapter boundary for driver operations. It delegates business logic
 * and ownership validation to {@link DriverService}, ensuring web layer concerns (HTTP status
 * codes, request body parsing, input validation) remain cleanly decoupled from core domain rules.</p>
 */
@RestController
@RequestMapping("/api/v1/drivers")
public class DriverController {

    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    /**
     * Registers a new driver operational profile and vehicle.
     *
     * @param authentication Injected security context holding authenticated principal
     * @param request        Validated driver and vehicle registration details
     * @return 201 Created with Location header pointing to the created driver resource
     */
    @PostMapping("/profile")
    public ResponseEntity<DriverResponse> create(Authentication authentication,
                                                 @Valid @RequestBody ProfileRequest request) {
        DriverResponse response = driverService.create(authentication, request);
        return ResponseEntity
                .created(URI.create("/api/v1/drivers/" + response.id()))
                .body(response);
    }

    /**
     * Updates driver availability status (ONLINE, OFFLINE, BUSY).
     *
     * @param authentication Authenticated driver security context
     * @param id             Target driver unique identifier
     * @param request        Validated availability status payload
     * @return Updated driver profile
     */
    @PutMapping("/{id}/availability")
    public DriverResponse availability(Authentication authentication,
                                       @PathVariable String id,
                                       @Valid @RequestBody AvailabilityRequest request) {
        return driverService.availability(authentication, id, request);
    }

    /**
     * Updates real-time geographic location and service zone.
     *
     * @param authentication Authenticated driver security context
     * @param id             Target driver unique identifier
     * @param request        Validated latitude, longitude, and zone
     * @return Updated driver profile
     */
    @PutMapping("/{id}/location")
    public DriverResponse location(Authentication authentication,
                                   @PathVariable String id,
                                   @Valid @RequestBody LocationRequest request) {
        return driverService.location(authentication, id, request);
    }

    /**
     * Discovers available drivers in a specific service zone.
     *
     * <p>Consumed synchronously by Ride Management Service to locate candidate drivers
     * during automated ride assignment.</p>
     *
     * @param zone Service zone to search (e.g., "central")
     * @return List of matching online drivers
     */
    @GetMapping("/available")
    public List<DriverResponse> available(@RequestParam String zone) {
        return driverService.available(zone);
    }

    /**
     * Retrieves an existing driver operational profile by unique ID.
     *
     * @param id Target driver unique identifier
     * @return Driver profile response
     */
    @GetMapping("/{id}")
    public DriverResponse get(@PathVariable String id) {
        return driverService.get(id);
    }
}
