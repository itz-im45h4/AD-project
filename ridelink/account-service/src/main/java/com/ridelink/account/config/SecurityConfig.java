package com.ridelink.account.config;

import com.ridelink.account.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for Account Service.
 *
 * <p>Configures a stateless security architecture tailored for microservices REST APIs:
 * <ul>
 *   <li>CSRF protection is disabled because the API uses stateless JWT tokens rather than browser session cookies.</li>
 *   <li>Session management is configured to STATELESS, preventing server-side session allocation.</li>
 *   <li>Fine-grained URL pattern authorization specifies public access, role-based access, and authenticated routes.</li>
 *   <li>Registers {@link JwtAuthenticationFilter} ahead of standard username-password authentication.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Password encoder utilizing BCrypt strong hashing algorithm with auto-generated salt.
     * Work factor default is 10 rounds, ensuring resistance to brute-force attacks.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Defines the HTTP security filter chain and request authorization policies.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter filter) throws Exception {
        return http
                // Disable CSRF since microservices API is stateless and does not rely on session cookies
                .csrf(csrf -> csrf.disable())

                // Stateless session management: do not store security context across HTTP requests
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Request authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public documentation and monitoring endpoints
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/health").permitAll()

                        // Public authentication endpoints for user onboarding and credential exchange
                        .requestMatchers(HttpMethod.POST, "/api/v1/accounts/register", "/api/v1/auth/login").permitAll()

                        // Administrative endpoints restricted strictly to ADMIN role
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/accounts/*/status").hasRole("ADMIN")

                        // All other application endpoints require a verified JWT principal
                        .anyRequest().authenticated()
                )

                // Insert JWT validation filter before Spring Security's default username/password filter
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
