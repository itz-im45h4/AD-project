package com.ridelink.account.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ridelink.account.domain.AccountStatus;
import com.ridelink.account.domain.Role;
import com.ridelink.account.dto.AccountDtos.AccountResponse;
import com.ridelink.account.dto.AccountDtos.LoginRequest;
import com.ridelink.account.dto.AccountDtos.LoginResponse;
import com.ridelink.account.dto.AccountDtos.RegisterRequest;
import com.ridelink.account.dto.AccountDtos.TokenUser;
import com.ridelink.account.exception.ApiExceptionHandler;
import com.ridelink.account.service.AccountService;
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
 * Controller-level MockMvc unit tests for AccountController.
 * Verifies HTTP serialization, status codes, and input validation mapping.
 */
@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock
    private AccountService service;

    @InjectMocks
    private AccountController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /accounts/register - Returns 201 Created and Location header on valid payload")
    void register_success() throws Exception {
        AccountResponse response = new AccountResponse(
                "acc-1", "Alice", "alice@example.com", "0771234567",
                Role.PASSENGER, AccountStatus.ACTIVE, Instant.now()
        );
        when(service.register(any(RegisterRequest.class))).thenReturn(response);

        String json = """
                {
                    "name": "Alice",
                    "email": "alice@example.com",
                    "phone": "0771234567",
                    "password": "Password123!",
                    "role": "PASSENGER"
                }
                """;

        mockMvc.perform(post("/api/v1/accounts/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/accounts/acc-1"))
                .andExpect(jsonPath("$.id").value("acc-1"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    @DisplayName("POST /accounts/register - Returns 400 Bad Request with ProblemDetail when validation fails")
    void register_validationFailure() throws Exception {
        String invalidJson = """
                {
                    "name": "",
                    "email": "not-an-email",
                    "phone": "",
                    "password": "short",
                    "role": null
                }
                """;

        mockMvc.perform(post("/api/v1/accounts/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("POST /auth/login - Returns 200 OK and token on successful credentials")
    void login_success() throws Exception {
        LoginResponse response = new LoginResponse(
                "mock.jwt.token", "Bearer", Instant.now().plusSeconds(3600),
                new TokenUser("acc-1", Role.PASSENGER, AccountStatus.ACTIVE)
        );
        when(service.login(any(LoginRequest.class))).thenReturn(response);

        String json = """
                {
                    "email": "alice@example.com",
                    "password": "Password123!"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("GET /accounts/me - Returns 200 OK with authenticated user profile")
    void me_success() throws Exception {
        AccountResponse response = new AccountResponse(
                "acc-1", "Alice", "alice@example.com", "0771234567",
                Role.PASSENGER, AccountStatus.ACTIVE, Instant.now()
        );
        when(service.me("acc-1")).thenReturn(response);

        var auth = new UsernamePasswordAuthenticationToken("acc-1", null);

        mockMvc.perform(get("/api/v1/accounts/me")
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("acc-1"))
                .andExpect(jsonPath("$.name").value("Alice"));
    }
}
