package com.ridelink.account.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * HTTP filter intercepting every incoming request to authenticate Bearer JWT tokens.
 *
 * <p>Extends Spring's {@link OncePerRequestFilter} to guarantee single execution per request
 * dispatch lifecycle. It extracts the {@code Authorization: Bearer <token>} header, validates
 * the cryptographic signature and expiration via {@link JwtService}, and establishes the
 * authenticated principal in Spring Security's thread-local {@link SecurityContextHolder}.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Extract Authorization header from incoming HTTP request
        String header = request.getHeader("Authorization");

        // Verify header presence and Bearer token scheme format
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7); // Strip 'Bearer ' prefix
            try {
                // Cryptographically verify token and parse identity/role claims
                JwtService.Claims claims = jwtService.verify(token);

                // Map user role into Spring Security GrantedAuthority with 'ROLE_' prefix
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name()));

                // Create authenticated principal token: principal = userId, credentials = null, authorities
                var authentication = new UsernamePasswordAuthenticationToken(
                        claims.userId(),
                        null,
                        authorities
                );

                // Populate security context for downstream controllers and authorization checks
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (IllegalArgumentException ignored) {
                // Invalid or expired token: leave SecurityContextHolder unauthenticated.
                // Downstream security filter chain will respond with 401 Unauthorized if endpoint requires auth.
            }
        }

        // Continue execution of the remaining filter chain
        chain.doFilter(request, response);
    }
}
