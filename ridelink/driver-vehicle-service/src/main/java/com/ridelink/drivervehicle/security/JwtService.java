package com.ridelink.drivervehicle.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Validates HMAC-SHA256 JWT tokens issued by Account Service.
 *
 * <p>Operates as a decentralized token verifier. Uses the shared HMAC secret
 * to verify the integrity and claims of incoming Bearer tokens without making
 * synchronous remote calls to the Account Service on each request.</p>
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
     * Validates signature, structural format, and temporal validity of incoming JWT.
     *
     * @param token Compact serialized JWT string
     * @return Claims record containing subject userId and authority role
     * @throws IllegalArgumentException if token is missing, expired, or signature is invalid
     */
    @SuppressWarnings("unchecked")
    public Claims verify(String token) {
        try {
            if (secret == null || secret.length() < 32) {
                throw new IllegalArgumentException("JWT_SECRET must be at least 32 characters");
            }

            // Split into header, payload, and signature segments
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Malformed token");
            }

            // Constant-time signature verification preventing timing side-channel attacks
            String expectedSignature = sign(parts[0] + "." + parts[1]);
            if (!java.security.MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("Signature mismatch");
            }

            // Decode payload JSON and parse claims
            Map<String, Object> payload = mapper.readValue(
                    Base64.getUrlDecoder().decode(parts[1]),
                    Map.class
            );

            // Check expiration timestamp against current epoch seconds
            long exp = ((Number) payload.get("exp")).longValue();
            if (Instant.now().getEpochSecond() >= exp) {
                throw new IllegalArgumentException("Token expired");
            }

            return new Claims(
                    (String) payload.get("sub"),
                    (String) payload.get("role")
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid or expired access token");
        }
    }

    /**
     * Generates HMAC-SHA256 hash using the shared secret key.
     */
    private String sign(String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal(content.getBytes(StandardCharsets.UTF_8))
        );
    }

    /** Verified subject claims extracted from JWT payload. */
    public record Claims(String userId, String role) { }
}
