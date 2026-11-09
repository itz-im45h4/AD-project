package com.ridelink.drivervehicle.config;

import com.ridelink.drivervehicle.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for Driver &amp; Vehicle Service.
 *
 * <p>Enforces stateless JWT authentication for all driver operations.
 * Account Service acts as the centralized token issuer, while Driver Service
 * validates the shared HMAC secret without maintaining local user credentials.</p>
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain security(HttpSecurity http, JwtAuthenticationFilter filter) throws Exception {
        return http
                // Disable CSRF since microservices API is stateless and does not rely on session cookies
                .csrf(csrf -> csrf.disable())

                // Stateless session policy: no HTTP session is stored across requests
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Request authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public API documentation
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/health").permitAll()

                        // All driver endpoints require a validated JWT principal
                        .requestMatchers("/api/v1/drivers/**").authenticated()
                        .anyRequest().authenticated()
                )

                // Intercept requests with JWT filter before standard Spring authentication
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
