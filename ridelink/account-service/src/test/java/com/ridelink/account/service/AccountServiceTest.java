package com.ridelink.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ridelink.account.domain.AccountStatus;
import com.ridelink.account.domain.Role;
import com.ridelink.account.domain.User;
import com.ridelink.account.dto.AccountDtos.*;
import com.ridelink.account.exception.ApiException;
import com.ridelink.account.repository.UserRepository;
import com.ridelink.account.security.JwtService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private UserRepository users;
    @Mock private PasswordEncoder passwords;
    @Mock private JwtService jwt;

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(users, passwords, jwt);
    }

    @Test
    @DisplayName("Should successfully register passenger account")
    void registerPassenger_success() {
        RegisterRequest request = new RegisterRequest("Alice", "alice@example.com", "0771112233", "Password123!", Role.PASSENGER);
        when(users.existsByEmailIgnoreCase("alice@example.com")).thenReturn(false);
        when(passwords.encode("Password123!")).thenReturn("hashed_pass");
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponse response = service.register(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Alice");
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.role()).isEqualTo(Role.PASSENGER);
        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should forbid ADMIN self-registration")
    void registerAdmin_forbidden() {
        RegisterRequest request = new RegisterRequest("Admin", "admin@example.com", "0771112233", "Password123!", Role.ADMIN);

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Administrator accounts cannot be self-registered")
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Should reject duplicate email registration with 409 Conflict")
    void registerDuplicateEmail_conflict() {
        RegisterRequest request = new RegisterRequest("Alice", "alice@example.com", "0771112233", "Password123!", Role.PASSENGER);
        when(users.existsByEmailIgnoreCase("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists")
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Should log in active user with valid credentials")
    void login_success() {
        LoginRequest request = new LoginRequest("alice@example.com", "Password123!");
        User user = new User();
        user.setId("user-1");
        user.setEmail("alice@example.com");
        user.setPasswordHash("hashed_pass");
        user.setRole(Role.PASSENGER);
        user.setStatus(AccountStatus.ACTIVE);

        when(users.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(user));
        when(passwords.matches("Password123!", "hashed_pass")).thenReturn(true);
        when(jwt.create("user-1", Role.PASSENGER)).thenReturn(new JwtService.Token("mock.jwt.token", Instant.now().plusSeconds(3600)));

        LoginResponse response = service.login(request);

        assertThat(response.accessToken()).isEqualTo("mock.jwt.token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().id()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("Should reject login with invalid password with 401 Unauthorized")
    void login_invalidPassword() {
        LoginRequest request = new LoginRequest("alice@example.com", "WrongPassword");
        User user = new User();
        user.setEmail("alice@example.com");
        user.setPasswordHash("hashed_pass");

        when(users.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(user));
        when(passwords.matches("WrongPassword", "hashed_pass")).thenReturn(false);

        assertThatThrownBy(() -> service.login(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid email or password")
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Should reject login if account is SUSPENDED with 403 Forbidden")
    void login_suspendedAccount() {
        LoginRequest request = new LoginRequest("alice@example.com", "Password123!");
        User user = new User();
        user.setEmail("alice@example.com");
        user.setPasswordHash("hashed_pass");
        user.setStatus(AccountStatus.SUSPENDED);

        when(users.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.of(user));
        when(passwords.matches("Password123!", "hashed_pass")).thenReturn(true);

        assertThatThrownBy(() -> service.login(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Account is not active")
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
