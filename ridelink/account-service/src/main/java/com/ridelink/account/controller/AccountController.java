package com.ridelink.account.controller;

import com.ridelink.account.dto.AccountDtos.*;
import com.ridelink.account.service.AccountService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class AccountController {
    private final AccountService service;
    public AccountController(AccountService service) { this.service = service; }
    @PostMapping("/accounts/register") ResponseEntity<AccountResponse> register(@Valid @RequestBody RegisterRequest request) { AccountResponse account = service.register(request); return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.id())).body(account); }
    @PostMapping("/auth/login") LoginResponse login(@Valid @RequestBody LoginRequest request) { return service.login(request); }
    @GetMapping("/accounts/me") AccountResponse me(Authentication auth) { return service.me(auth.getName()); }
    @PutMapping("/accounts/me") AccountResponse update(Authentication auth, @Valid @RequestBody UpdateProfileRequest request) { return service.update(auth.getName(), request); }
    @PatchMapping("/accounts/{id}/status") AccountResponse status(@PathVariable String id, @Valid @RequestBody StatusRequest request) { return service.updateStatus(id, request); }
}
