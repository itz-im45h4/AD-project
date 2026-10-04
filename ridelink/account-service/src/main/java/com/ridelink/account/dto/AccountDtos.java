package com.ridelink.account.dto;

import com.ridelink.account.domain.AccountStatus;
import com.ridelink.account.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Data Transfer Objects (DTOs) for Account Service HTTP API.
 *
 * <p>Separates external JSON contracts from internal MongoDB storage schema.
 * Enforces validation constraints on user inputs (e.g. Email validity, minimum password lengths).</p>
 */
public final class AccountDtos {

    private AccountDtos() {
    }

    /** Request payload for registering a new user account. */
    public record RegisterRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email String email,
            @NotBlank @Size(max = 30) String phone,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotNull Role role
    ) { }

    /** Request payload for authenticating user credentials. */
    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) { }

    /** Request payload for updating editable profile fields. */
    public record UpdateProfileRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email String email,
            @NotBlank @Size(max = 30) String phone
    ) { }

    /** Request payload for administrative account status updates (ACTIVE/SUSPENDED). */
    public record StatusRequest(
            @NotNull AccountStatus status
    ) { }

    /** Public user profile representation excluding passwordHash. */
    public record AccountResponse(
            String id,
            String name,
            String email,
            String phone,
            Role role,
            AccountStatus status,
            Instant createdAt
    ) { }

    /** Authentication response payload containing signed JWT access token. */
    public record LoginResponse(
            String accessToken,
            String tokenType,
            Instant expiresAt,
            TokenUser user
    ) { }

    /** Identity summary embedded in login response. */
    public record TokenUser(
            String id,
            Role role,
            AccountStatus status
    ) { }
}
