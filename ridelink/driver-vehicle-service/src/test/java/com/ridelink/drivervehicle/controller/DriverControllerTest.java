package com.ridelink.drivervehicle.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ridelink.drivervehicle.domain.Availability;
import com.ridelink.drivervehicle.dto.DriverDtos.DriverResponse;
import com.ridelink.drivervehicle.dto.DriverDtos.ProfileRequest;
import com.ridelink.drivervehicle.dto.DriverDtos.VehicleResponse;
import com.ridelink.drivervehicle.exception.ApiExceptionHandler;
import com.ridelink.drivervehicle.service.DriverService;
import java.time.Instant;
import java.util.List;
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
 * Controller-level MockMvc unit tests for DriverController.
 * Verifies HTTP serialization, status codes, and endpoint routing.
 */
@ExtendWith(MockitoExtension.class)
class DriverControllerTest {

    @Mock
    private DriverService service;

    @InjectMocks
    private DriverController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /drivers/profile - Returns 201 Created and Location header on valid profile")
    void createProfile_success() throws Exception {
        DriverResponse response = new DriverResponse(
                "driver-1", "user-1", "DL-12345", Availability.OFFLINE, "ZONE_A",
                6.9271, 79.8612, new VehicleResponse("Toyota", "Prius", "CAB-1122", 4), Instant.now()
        );
        when(service.create(any(), any(ProfileRequest.class))).thenReturn(response);

        var auth = new UsernamePasswordAuthenticationToken("user-1", null);

        String json = """
                {
                    "userId": "user-1",
                    "licenseNumber": "DL-12345",
                    "vehicle": {
                        "make": "Toyota",
                        "model": "Prius",
                        "plateNumber": "CAB-1122",
                        "capacity": 4
                    },
                    "serviceZone": "ZONE_A"
                }
                """;

        mockMvc.perform(post("/api/v1/drivers/profile")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/drivers/driver-1"))
                .andExpect(jsonPath("$.id").value("driver-1"))
                .andExpect(jsonPath("$.availability").value("OFFLINE"));
    }

    @Test
    @DisplayName("GET /drivers/{id} - Returns 200 OK with driver profile")
    void getDriver_success() throws Exception {
        DriverResponse response = new DriverResponse(
                "driver-1", "user-1", "DL-12345", Availability.ONLINE, "ZONE_A",
                6.9271, 79.8612, new VehicleResponse("Toyota", "Prius", "CAB-1122", 4), Instant.now()
        );
        when(service.get("driver-1")).thenReturn(response);

        mockMvc.perform(get("/api/v1/drivers/driver-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("driver-1"))
                .andExpect(jsonPath("$.licenseNumber").value("DL-12345"));
    }

    @Test
    @DisplayName("GET /drivers/available - Returns 200 OK with online drivers for requested zone")
    void available_success() throws Exception {
        DriverResponse response = new DriverResponse(
                "driver-1", "user-1", "DL-12345", Availability.ONLINE, "ZONE_A",
                6.9271, 79.8612, new VehicleResponse("Toyota", "Prius", "CAB-1122", 4), Instant.now()
        );
        when(service.available(eq("ZONE_A"))).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/drivers/available")
                        .param("zone", "ZONE_A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value("driver-1"));
    }
}
