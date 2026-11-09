package com.ridelink.account.security;

import tools.jackson.databind.ObjectMapper;
import com.ridelink.account.domain.Role;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Lightweight, zero-dependency JWT service implementing HMAC-SHA256 (HS256).
 *
 * <p>In this microservices architecture, JSON Web Tokens provide stateless authentication
 * and authorization across service boundaries. Each token encapsulates identity (userId)
 * and authorization (role) claims signed cryptographically with a shared secret key,
 * removing the need for a centralized session store or database lookup on every request.</p>
 */
@Service
public class JwtService {

    private final String secret;
    private final ObjectMapper mapper;

    public JwtService(@Value("${app.security.jwt-secret:}") String secret, ObjectMapper mapper) {
        this.secret = secret;
        this.mapper = mapper;
    }

    /**
     * Issues a signed JWT access token for an authenticated user.
     *
     * <p>Token lifespan is set to 1 hour (3600 seconds) to mitigate exposure in case
     * of credential leakage. The token payload contains:
     * <ul>
     *   <li>{@code sub} - Subject identifier (User UUID)</li>
     *   <li>{@code role} - Authority role (PASSENGER, DRIVER, ADMIN)</li>
     *   <li>{@code exp} - Unix epoch timestamp marking token expiration</li>
     * </ul>
     *
     * @param userId Unique identifier of the authenticated user
     * @param role   Authorization role assigned to the user
     * @return Token record containing the raw compact JWT string and expiration Instant
     */
    public Token create(String userId, Role role) {
        try {
            // Verify secret integrity before issuing tokens
            requireSecret();
            Instant expiresAt = Instant.now().plusSeconds(3600);

            // 1. JWT Header specifying HS256 algorithm and JWT type
            String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");

            // 2. JWT Payload containing claims serialized as JSON
            String payload = encode(mapper.writeValueAsString(Map.of(
                    "sub", userId,
                    "role", role.name(),
                    "exp", expiresAt.getEpochSecond()
            )));

            // 3. Cryptographic signature over (Header + "." + Payload)
            String signature = sign(header + "." + payload);

            // Construct RFC 7519 compact representation: header.payload.signature
            return new Token(header + "." + payload + "." + signature, expiresAt);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not create access token", ex);
        }
    }

    /**
     * Cryptographically verifies a compact JWT string and extracts its claims.
     *
     * <p>Verification ensures:
     * 1. Token structure adheres to the 3-part compact serialization (header.payload.signature).
     * 2. The signature matches the HMAC-SHA256 calculation computed with the local secret.
     * 3. Constant-time byte comparison is used to defend against side-channel timing attacks.
     * 4. Current system timestamp has not exceeded the {@code exp} expiration claim.</p>
     *
     * @param token Compact serialized JWT string
     * @return Claims record containing verified userId and Role
     * @throws IllegalArgumentException if token is tampered, malformed, or expired
     */
    @SuppressWarnings("unchecked")
    public Claims verify(String token) {
        try {
            requireSecret();

            // Split into header, payload, and signature components
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Malformed token: expected 3 dot-separated segments");
            }

            // Verify signature using constant-time comparison to prevent timing attacks
            String expectedSignature = sign(parts[0] + "." + parts[1]);
            if (!constantEquals(expectedSignature, parts[2])) {
                throw new IllegalArgumentException("Invalid token signature");
            }

            // Decode base64url payload and deserialize claims
            Map<String, Object> payload = mapper.readValue(
                    Base64.getUrlDecoder().decode(parts[1]),
                    Map.class
            );

            // Verify temporal validity (expiration)
            long exp = ((Number) payload.get("exp")).longValue();
            if (Instant.now().getEpochSecond() >= exp) {
                throw new IllegalArgumentException("Token has expired");
            }

            return new Claims(
                    (String) payload.get("sub"),
                    Role.valueOf((String) payload.get("role"))
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid or expired access token");
        }
    }

    /**
     * Enforces minimum cryptographic key length for HMAC-SHA256 (256 bits / 32 bytes).
     */
    private void requireSecret() {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be configured and at least 32 characters long");
        }
    }

    /**
     * Computes HMAC-SHA256 signature for given content and encodes it as Base64URL without padding.
     */
    private String sign(String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal(content.getBytes(StandardCharsets.UTF_8))
        );
    }

    /**
     * Encodes a raw UTF-8 string into Base64URL without padding per RFC 7515 specification.
     */
    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Compares two strings in constant time to prevent side-channel timing analysis attacks.
     * Uses MessageDigest.isEqual which performs a time-invariant byte comparison.
     */
    private boolean constantEquals(String one, String two) {
        return java.security.MessageDigest.isEqual(
                one.getBytes(StandardCharsets.UTF_8),
                two.getBytes(StandardCharsets.UTF_8)
        );
    }

    /** DTO representing generated token and expiration time. */
    public record Token(String value, Instant expiresAt) { }

    /** DTO representing authenticated subject claims extracted from verified token. */
    public record Claims(String userId, Role role) { }
}
