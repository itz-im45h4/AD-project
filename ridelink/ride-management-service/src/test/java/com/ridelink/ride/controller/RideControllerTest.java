package com.ridelink.ride.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ridelink.ride.domain.RideStatus;
import com.ridelink.ride.dto.RideDtos.CreateRequest;
import com.ridelink.ride.dto.RideDtos.Location;
import com.ridelink.ride.dto.RideDtos.RideResponse;
import com.ridelink.ride.exception.ApiExceptionHandler;
import com.ridelink.ride.service.RideService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Controller-level MockMvc unit tests for RideController.
 * Verifies request validation, Location headers, and state transition routing.
 */
@ExtendWith(MockitoExtension.class)
class RideControllerTest {

    @Mock
    private RideService service;

    @InjectMocks
    private RideController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /rides - Returns 201 Created and Location header on valid ride request")
    void createRide_success() throws Exception {
        RideResponse response = new RideResponse(
                "ride-1", "user-pass-1", null, null, RideStatus.REQUESTED,
                new Location("Pickup St", "ZONE_A", 6.9271, 79.8612),
                new Location("Dest St", "ZONE_A", 6.9300, 79.8700),
                null, "PENDING", Instant.now()
        );
        when(service.create(any(), any(CreateRequest.class))).thenReturn(response);

        var auth = new UsernamePasswordAuthenticationToken("user-pass-1", null);

        String json = """
                {
                    "passengerId": "user-pass-1",
                    "pickup": {
                        "address": "Pickup St",
                        "zone": "ZONE_A",
                        "latitude": 6.9271,
                        "longitude": 79.8612
                    },
                    "destination": {
                        "address": "Dest St",
                        "zone": "ZONE_A",
                        "latitude": 6.9300,
                        "longitude": 79.8700
                    }
                }
                """;

        mockMvc.perform(post("/api/v1/rides")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/rides/ride-1"))
                .andExpect(jsonPath("$.id").value("ride-1"))
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    @DisplayName("GET /rides/{id} - Returns 200 OK with ride details")
    void getRide_success() throws Exception {
        RideResponse response = new RideResponse(
                "ride-1", "user-pass-1", "driver-1", "user-driver-1", RideStatus.ACCEPTED,
                new Location("Pickup St", "ZONE_A", 6.9271, 79.8612),
                new Location("Dest St", "ZONE_A", 6.9300, 79.8700),
                new BigDecimal("500.00"), "PENDING", Instant.now()
        );
        when(service.get(any(), eq("ride-1"))).thenReturn(response);

        var auth = new UsernamePasswordAuthenticationToken("user-pass-1", null);

        mockMvc.perform(get("/api/v1/rides/ride-1")
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ride-1"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @DisplayName("POST /rides - Returns 400 Bad Request when pickup or passengerId is missing")
    void createRide_validationFailure() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken("user-pass-1", null);

        String invalidJson = """
                {
                    "passengerId": "",
                    "pickup": null,
                    "destination": null
                }
                """;

        mockMvc.perform(post("/api/v1/rides")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"));
    }
}
