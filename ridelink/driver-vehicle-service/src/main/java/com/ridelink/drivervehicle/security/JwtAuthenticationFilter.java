package com.ridelink.drivervehicle.security;

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
 * Filter intercepting incoming requests to extract and validate JWT Bearer tokens.
 *
 * <p>Translates verified JWT claims into Spring Security's {@link SecurityContextHolder},
 * populating the principal with the user ID and granted role authority.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwt;

    public JwtAuthenticationFilter(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            try {
                JwtService.Claims claims = jwt.verify(header.substring(7));
                var authentication = new UsernamePasswordAuthenticationToken(
                        claims.userId(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + claims.role()))
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (IllegalArgumentException ignored) {
                // If token verification fails, the context remains unauthenticated.
            }
        }

        chain.doFilter(request, response);
    }
}
