package com.ridelink.account.controller;

import com.ridelink.account.dto.AccountDtos.AccountResponse;
import com.ridelink.account.dto.AccountDtos.LoginRequest;
import com.ridelink.account.dto.AccountDtos.LoginResponse;
import com.ridelink.account.dto.AccountDtos.RegisterRequest;
import com.ridelink.account.dto.AccountDtos.StatusRequest;
import com.ridelink.account.dto.AccountDtos.UpdateProfileRequest;
import com.ridelink.account.service.AccountService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller Layer: HTTP Presentation Adapter for Account & Authentication endpoints.
 * Responsibilities:
 * - Maps incoming HTTP requests to corresponding service methods.
 * - Triggers bean validation on request payloads via @Valid.
 * - Resolves authenticated user identity from Spring Security Context.
 * - Returns appropriate HTTP status codes (201 Created with Location header, 200 OK).
 * Contains zero business rules or direct database access (Single Responsibility Principle).
 */
@RestController
@RequestMapping("/api/v1")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    /**
     * Endpoint: POST /api/v1/accounts/register
     * Public endpoint to register a new passenger or driver.
     * Returns 201 Created along with the Location header pointing to the new account URI.
     */
    @PostMapping("/accounts/register")
    public ResponseEntity<AccountResponse> register(@Valid @RequestBody RegisterRequest request) {
        AccountResponse account = service.register(request);
        return ResponseEntity
                .created(URI.create("/api/v1/accounts/" + account.id()))
                .body(account);
    }

    /**
     * Endpoint: POST /api/v1/auth/login
     * Public endpoint to authenticate user credentials and obtain a JWT bearer token.
     */
    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request);
    }

    /**
     * Endpoint: GET /api/v1/accounts/me
     * Protected endpoint returning the profile of the authenticated caller.
     * Injected Authentication object provides the authenticated principal (user ID) from the JWT.
     */
    @GetMapping("/accounts/me")
    public AccountResponse me(Authentication auth) {
        return service.me(auth.getName());
    }

    /**
     * Endpoint: PUT /api/v1/accounts/me
     * Protected endpoint allowing the authenticated user to update their profile details.
     */
    @PutMapping("/accounts/me")
    public AccountResponse update(Authentication auth, @Valid @RequestBody UpdateProfileRequest request) {
        return service.update(auth.getName(), request);
    }

    /**
     * Endpoint: PATCH /api/v1/accounts/{id}/status
     * Privileged endpoint to activate, suspend, or deactivate an account.
     * Access is restricted to users with ROLE_ADMIN in SecurityConfig.
     */
    @PatchMapping("/accounts/{id}/status")
    public AccountResponse status(@PathVariable String id, @Valid @RequestBody StatusRequest request) {
        return service.updateStatus(id, request);
    }
}
