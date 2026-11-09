package com.ridelink.farepayment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ridelink.farepayment.domain.PaymentStatus;
import com.ridelink.farepayment.dto.FareDtos.EstimateRequest;
import com.ridelink.farepayment.dto.FareDtos.EstimateResponse;
import com.ridelink.farepayment.dto.FareDtos.FareResponse;
import com.ridelink.farepayment.dto.FareDtos.FinalizeRequest;
import com.ridelink.farepayment.dto.FareDtos.ReceiptResponse;
import com.ridelink.farepayment.exception.ApiExceptionHandler;
import com.ridelink.farepayment.service.FareService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Controller-level MockMvc unit tests for FareController.
 * Verifies pricing calculation endpoints, fare finalization, and receipt retrieval.
 */
@ExtendWith(MockitoExtension.class)
class FareControllerTest {

    @Mock
    private FareService service;

    @InjectMocks
    private FareController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /fares/estimate - Returns 200 OK with estimated fare calculation")
    void estimate_success() throws Exception {
        EstimateResponse response = new EstimateResponse(
                new BigDecimal("550.00"), "LKR",
                new BigDecimal("200.00"), new BigDecimal("50.00"), new BigDecimal("10.00")
        );
        when(service.estimate(any(EstimateRequest.class))).thenReturn(response);

        String json = """
                {
                    "pickup": "Colombo",
                    "destination": "Kandy",
                    "distanceKm": 5.0,
                    "durationMinutes": 10.0
                }
                """;

        mockMvc.perform(post("/api/v1/fares/estimate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedFare").value(550.00))
                .andExpect(jsonPath("$.currency").value("LKR"));
    }

    @Test
    @DisplayName("POST /fares/finalize - Returns 200 OK with finalized fare in PENDING status")
    void finalizeFare_success() throws Exception {
        FareResponse response = new FareResponse(
                "fare-1", "ride-1", new BigDecimal("550.00"), "LKR", PaymentStatus.PENDING
        );
        when(service.finalize(any(FinalizeRequest.class))).thenReturn(response);

        String json = """
                {
                    "rideId": "ride-1",
                    "distanceKm": 5.0,
                    "durationMinutes": 10.0
                }
                """;

        mockMvc.perform(post("/api/v1/fares/finalize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fareId").value("fare-1"))
                .andExpect(jsonPath("$.finalFare").value(550.00))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /fares/{rideId} - Returns 200 OK with finalized fare record")
    void getFare_success() throws Exception {
        FareResponse response = new FareResponse(
                "fare-1", "ride-1", new BigDecimal("550.00"), "LKR", PaymentStatus.PENDING
        );
        when(service.getFare(eq("ride-1"))).thenReturn(response);

        mockMvc.perform(get("/api/v1/fares/ride-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fareId").value("fare-1"))
                .andExpect(jsonPath("$.finalFare").value(550.00));
    }

    @Test
    @DisplayName("GET /receipts/{rideId} - Returns 200 OK with issued receipt")
    void receipt_success() throws Exception {
        ReceiptResponse response = new ReceiptResponse(
                "receipt-1", "ride-1", "pay-1", new BigDecimal("550.00"),
                "LKR", PaymentStatus.SUCCESS, Instant.now()
        );
        when(service.receipt(eq("ride-1"))).thenReturn(response);

        mockMvc.perform(get("/api/v1/receipts/ride-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptId").value("receipt-1"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }
}
