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

@Configuration @EnableWebSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter filter) throws Exception {
        return http.csrf(csrf -> csrf.disable()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/accounts/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/accounts/*/status").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class).build();
    }
}
