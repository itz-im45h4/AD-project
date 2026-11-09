package com.ridelink.account.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * MongoDB document representing an authenticated User account in the system.
 *
 * <p><strong>Data Ownership &amp; Security Boundaries:</strong>
 * <ul>
 *   <li>Owned exclusively by Account Service in MongoDB {@code users} collection.</li>
 *   <li>{@code passwordHash} stores the BCrypt hash with salt and is never exposed in API DTOs.</li>
 *   <li>Downstream microservices (Ride, Driver, Fare) only store the {@code userId} string
 *       as an opaque foreign identifier, never duplicating credentials or personal profiles.</li>
 * </ul>
 */
@Document("users")
public class User {

    @Id
    private String id;
    private String name;
    private String email;
    private String phone;
    private String passwordHash;
    private Role role;
    private AccountStatus status;
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
