package com.ridelink.farepayment.config;

import org.springframework.context.annotation.*;import org.springframework.security.config.annotation.web.builders.HttpSecurity;import org.springframework.security.config.http.SessionCreationPolicy;import org.springframework.security.web.SecurityFilterChain;

/** JWT enforcement is supplied by Account Service during integration; the Ride Management finalization call is service-to-service. */
@Configuration public class SecurityConfig {@Bean SecurityFilterChain security(HttpSecurity http)throws Exception{return http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(a->a.requestMatchers("/swagger-ui/**","/v3/api-docs/**").permitAll().anyRequest().permitAll()).build();}}
