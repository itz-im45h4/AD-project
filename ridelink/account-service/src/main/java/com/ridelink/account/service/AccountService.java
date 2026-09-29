package com.ridelink.account.service;

import com.ridelink.account.domain.*;
import com.ridelink.account.dto.AccountDtos.*;
import com.ridelink.account.exception.ApiException;
import com.ridelink.account.repository.UserRepository;
import com.ridelink.account.security.JwtService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AccountService {
    private final UserRepository users; private final PasswordEncoder passwords; private final JwtService jwt;
    public AccountService(UserRepository users, PasswordEncoder passwords, JwtService jwt) { this.users = users; this.passwords = passwords; this.jwt = jwt; }
    public AccountResponse register(RegisterRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        User user = new User(); user.setId(UUID.randomUUID().toString()); user.setName(request.name()); user.setEmail(request.email().trim().toLowerCase());
        user.setPhone(request.phone()); user.setPasswordHash(passwords.encode(request.password())); user.setRole(request.role()); user.setStatus(AccountStatus.ACTIVE); user.setCreatedAt(Instant.now());
        return toResponse(users.save(user));
    }
    public LoginResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email()).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (!passwords.matches(request.password(), user.getPasswordHash())) throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        if (user.getStatus() != AccountStatus.ACTIVE) throw new ApiException(HttpStatus.FORBIDDEN, "Account is not active");
        JwtService.Token token = jwt.create(user.getId(), user.getRole());
        return new LoginResponse(token.value(), "Bearer", token.expiresAt(), new TokenUser(user.getId(), user.getRole(), user.getStatus()));
    }
    public AccountResponse me(String id) { return toResponse(require(id)); }
    public AccountResponse update(String id, UpdateProfileRequest request) {
        User user = require(id); users.findByEmailIgnoreCase(request.email()).filter(other -> !other.getId().equals(id)).ifPresent(other -> { throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists"); });
        user.setName(request.name()); user.setEmail(request.email().trim().toLowerCase()); user.setPhone(request.phone()); return toResponse(users.save(user));
    }
    public AccountResponse updateStatus(String id, StatusRequest request) { User user = require(id); user.setStatus(request.status()); return toResponse(users.save(user)); }
    private User require(String id) { return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found")); }
    private AccountResponse toResponse(User u) { return new AccountResponse(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole(), u.getStatus(), u.getCreatedAt()); }
}
