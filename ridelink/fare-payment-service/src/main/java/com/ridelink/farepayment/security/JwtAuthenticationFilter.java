package com.ridelink.farepayment.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Filter that intercepts incoming HTTP requests, verifies HMAC-SHA256 JWT tokens,
 * and populates the Spring Security context for Fare &amp; Payment Service.
 *
 * <p>Validates tokens issued by Account Service using the shared cryptographic secret:
 * <ul>
 *   <li>Extracts Bearer token from the {@code Authorization} header.</li>
 *   <li>Verifies HMAC-SHA256 signature using constant-time comparison to prevent timing attacks.</li>
 *   <li>Validates token expiration (exp claim).</li>
 *   <li>Populates {@link SecurityContextHolder} with authenticated user ID and role authority.</li>
 * </ul>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final String secret;
    private final ObjectMapper mapper;

    public JwtAuthenticationFilter(@Value("${app.security.jwt-secret:}") String secret,
                                   ObjectMapper mapper) {
        this.secret = secret;
        this.mapper = mapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            try {
                String token = header.substring(7);
                String[] parts = token.split("\\.");

                // Validate token format and minimum secret strength
                if (parts.length != 3 || secret == null || secret.length() < 32) {
                    throw new IllegalArgumentException("Malformed token or secret configuration");
                }

                // Verify cryptographic signature in constant time
                String expectedSignature = sign(parts[0] + "." + parts[1]);
                if (!java.security.MessageDigest.isEqual(
                        expectedSignature.getBytes(StandardCharsets.UTF_8),
                        parts[2].getBytes(StandardCharsets.UTF_8))) {
                    throw new IllegalArgumentException("Signature verification failed");
                }

                // Parse payload JSON and extract claims
                Map<String, Object> claims = mapper.readValue(
                        Base64.getUrlDecoder().decode(parts[1]),
                        Map.class
                );

                // Check expiration
                long exp = ((Number) claims.get("exp")).longValue();
                if (Instant.now().getEpochSecond() >= exp) {
                    throw new IllegalArgumentException("Token has expired");
                }

                // Establish authenticated principal and granted authority in SecurityContext
                String userId = (String) claims.get("sub");
                String role = (String) claims.get("role");
                var auth = new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ignored) {
                // If token is invalid or expired, proceed unauthenticated.
                // SecurityFilterChain will reject with 401 Unauthorized if endpoint requires auth.
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Generates HMAC-SHA256 signature for token segments using the shared secret.
     */
    private String sign(String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal(content.getBytes(StandardCharsets.UTF_8))
        );
    }
}
