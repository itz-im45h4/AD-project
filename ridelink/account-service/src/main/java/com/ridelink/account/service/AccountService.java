package com.ridelink.account.service;

import com.ridelink.account.domain.AccountStatus;
import com.ridelink.account.domain.Role;
import com.ridelink.account.domain.User;
import com.ridelink.account.dto.AccountDtos.AccountResponse;
import com.ridelink.account.dto.AccountDtos.LoginRequest;
import com.ridelink.account.dto.AccountDtos.LoginResponse;
import com.ridelink.account.dto.AccountDtos.RegisterRequest;
import com.ridelink.account.dto.AccountDtos.StatusRequest;
import com.ridelink.account.dto.AccountDtos.TokenUser;
import com.ridelink.account.dto.AccountDtos.UpdateProfileRequest;
import com.ridelink.account.exception.ApiException;
import com.ridelink.account.repository.UserRepository;
import com.ridelink.account.security.JwtService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Service Layer for Account & Identity management.
 * Encapsulates core business rules for user registration, authentication,
 * password hashing, profile updates, and account status transitions.
 * Keeps business logic strictly decoupled from HTTP presentation and database access.
 */
@Service
public class AccountService {

    // Injected repository for MongoDB data access
    private final UserRepository users;
    // Injected BCrypt password encoder for one-way credential hashing
    private final PasswordEncoder passwords;
    // Injected token service for stateless HMAC JWT creation
    private final JwtService jwt;

    public AccountService(UserRepository users, PasswordEncoder passwords, JwtService jwt) {
        this.users = users;
        this.passwords = passwords;
        this.jwt = jwt;
    }

    /**
     * Registers a new passenger or driver account.
     * Enforces role restrictions and email uniqueness before persisting credentials.
     */
    public AccountResponse register(RegisterRequest request) {
        // Business Rule: Self-service registration is strictly limited to PASSENGER and DRIVER roles.
        // Administrative accounts require manual or privileged provisioning to prevent privilege escalation.
        if (request.role() == Role.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Administrator accounts cannot be self-registered");
        }

        // Domain invariant: Email addresses must be unique across the platform (case-insensitive check).
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }

        // Map request DTO to database entity
        User user = new User();
        user.setId(UUID.randomUUID().toString()); // Generate immutable unique identifier
        user.setName(request.name());
        user.setEmail(request.email().trim().toLowerCase()); // Normalize email to lowercase
        user.setPhone(request.phone());
        // Security: One-way hash password using BCrypt with salt before saving to database
        user.setPasswordHash(passwords.encode(request.password()));
        user.setRole(request.role());
        user.setStatus(AccountStatus.ACTIVE); // Default lifecycle state for newly registered accounts
        user.setCreatedAt(Instant.now());

        // Persist to MongoDB 'users' collection and map to clean external response DTO
        return toResponse(users.save(user));
    }

    /**
     * Authenticates user credentials and issues a stateless access token.
     */
    public LoginResponse login(LoginRequest request) {
        // Look up user by normalized email
        User user = users.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        // Compare raw submitted password with stored BCrypt hash
        // Notice the generic error message to prevent user enumeration attacks
        if (!passwords.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        // Verify account lifecycle status: only ACTIVE accounts may receive an access token
        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is not active");
        }

        // Issue stateless HS256 JWT containing userId and assigned role
        JwtService.Token token = jwt.create(user.getId(), user.getRole());

        // Return token and safe user metadata (excluding password hash)
        return new LoginResponse(
                token.value(),
                "Bearer",
                token.expiresAt(),
                new TokenUser(user.getId(), user.getRole(), user.getStatus())
        );
    }

    /**
     * Retrieves the profile of the authenticated caller by user ID.
     */
    public AccountResponse me(String id) {
        return toResponse(require(id));
    }

    /**
     * Updates profile details (name, email, phone).
     * Ensures new email does not collide with another user's account.
     */
    public AccountResponse update(String id, UpdateProfileRequest request) {
        User user = require(id);

        // Check if another account is already registered with the requested new email
        users.findByEmailIgnoreCase(request.email())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
                });

        user.setName(request.name());
        user.setEmail(request.email().trim().toLowerCase());
        user.setPhone(request.phone());
        return toResponse(users.save(user));
    }

    /**
     * Updates account lifecycle status (ACTIVE, SUSPENDED, DEACTIVATED).
     * Protected at controller level to require ROLE_ADMIN.
     */
    public AccountResponse updateStatus(String id, StatusRequest request) {
        User user = require(id);
        user.setStatus(request.status());
        return toResponse(users.save(user));
    }

    // Helper method to retrieve user by ID or throw 404 Not Found
    private User require(String id) {
        return users.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    // Helper method to convert internal MongoDB domain entity to safe public response DTO
    private AccountResponse toResponse(User u) {
        return new AccountResponse(
                u.getId(),
                u.getName(),
                u.getEmail(),
                u.getPhone(),
                u.getRole(),
                u.getStatus(),
                u.getCreatedAt()
        );
    }
}
