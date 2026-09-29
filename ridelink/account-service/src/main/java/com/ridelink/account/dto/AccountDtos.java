package com.ridelink.account.dto;

import java.time.Instant;
import com.ridelink.account.domain.AccountStatus;
import com.ridelink.account.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AccountDtos {
    private AccountDtos() { }
    public record RegisterRequest(@NotBlank @Size(max = 100) String name, @NotBlank @Email String email,
            @NotBlank @Size(max = 30) String phone, @NotBlank @Size(min = 8, max = 72) String password,
            @NotNull Role role) { }
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) { }
    public record UpdateProfileRequest(@NotBlank @Size(max = 100) String name, @NotBlank @Email String email,
            @NotBlank @Size(max = 30) String phone) { }
    public record StatusRequest(@NotNull AccountStatus status) { }
    public record AccountResponse(String id, String name, String email, String phone, Role role,
            AccountStatus status, Instant createdAt) { }
    public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, TokenUser user) { }
    public record TokenUser(String id, Role role, AccountStatus status) { }
}
