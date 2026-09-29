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

@Service
public class JwtService {
    private final String secret;
    private final ObjectMapper mapper;
    public JwtService(@Value("${app.security.jwt-secret:}") String secret, ObjectMapper mapper) { this.secret = secret; this.mapper = mapper; }
    public Token create(String userId, Role role) {
        try {
            requireSecret(); Instant expiresAt = Instant.now().plusSeconds(3600);
            String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
            String payload = encode(mapper.writeValueAsString(Map.of("sub", userId, "role", role.name(), "exp", expiresAt.getEpochSecond())));
            return new Token(header + "." + payload + "." + sign(header + "." + payload), expiresAt);
        } catch (Exception ex) { throw new IllegalStateException("Could not create access token", ex); }
    }
    @SuppressWarnings("unchecked")
    public Claims verify(String token) {
        try {
            requireSecret(); String[] parts = token.split("\\.");
            if (parts.length != 3 || !constantEquals(sign(parts[0] + "." + parts[1]), parts[2])) throw new IllegalArgumentException();
            Map<String, Object> payload = mapper.readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
            long exp = ((Number) payload.get("exp")).longValue();
            if (Instant.now().getEpochSecond() >= exp) throw new IllegalArgumentException();
            return new Claims((String) payload.get("sub"), Role.valueOf((String) payload.get("role")));
        } catch (Exception ex) { throw new IllegalArgumentException("Invalid or expired access token"); }
    }
    private void requireSecret() { if (secret == null || secret.length() < 32) throw new IllegalStateException("JWT_SECRET must be at least 32 characters"); }
    private String sign(String content) throws Exception { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")); return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8))); }
    private String encode(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private boolean constantEquals(String one, String two) { return java.security.MessageDigest.isEqual(one.getBytes(StandardCharsets.UTF_8), two.getBytes(StandardCharsets.UTF_8)); }
    public record Token(String value, Instant expiresAt) { }
    public record Claims(String userId, Role role) { }
}
