package com.ridelink.ride.config;

import com.ridelink.ride.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for Ride Management Service.
 *
 * <p>Configures stateless API security:
 * <ul>
 *   <li>CSRF disabled for stateless REST architecture.</li>
 *   <li>STATELESS session management preventing server-side session state.</li>
 *   <li>Swagger/OpenAPI documentation endpoints permitAll.</li>
 *   <li>All ride lifecycle endpoints require authenticated JWT.</li>
 *   <li>Registers {@link JwtAuthenticationFilter} ahead of standard authentication.</li>
 * </ul>
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
                        // Public OpenAPI documentation
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/health").permitAll()

                        // All ride management endpoints require an authenticated JWT principal
                        .anyRequest().authenticated()
                )

                // Add JWT validation filter before standard Spring authentication
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
