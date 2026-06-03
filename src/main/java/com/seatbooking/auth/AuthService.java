package com.seatbooking.auth;

import com.seatbooking.model.Member;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.mindrot.jbcrypt.BCrypt;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

public class AuthService {

    private static final long TOKEN_TTL_MS = 8 * 60 * 60 * 1000L; // 8-hour workday session

    // 46-byte key → satisfies HS256's 256-bit minimum
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(
        "SeatBookingJwtSecret2024ForHmacSha256Algorithm!!".getBytes(StandardCharsets.UTF_8)
    );

    // ── Token operations ──────────────────────────────────────────────────────

    public static String generateToken(Member member) {
        return Jwts.builder()
                .subject(member.getId())
                .claim("name", member.getName())
                .claim("role", member.getRole().name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + TOKEN_TTL_MS))
                .signWith(SECRET_KEY)
                .compact();
    }

    /** Returns verified claims, or empty if the token is invalid or expired. */
    public static Optional<Claims> parseToken(String token) {
        if (token == null) return Optional.empty();
        try {
            return Optional.of(
                Jwts.parser()
                    .verifyWith(SECRET_KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
            );
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static boolean isTokenAdmin(String token) {
        return parseToken(token)
                .map(c -> "ADMIN".equals(c.get("role", String.class)))
                .orElse(false);
    }

    public static Optional<String> getSubject(String token) {
        return parseToken(token).map(Claims::getSubject);
    }

    // ── Password operations ───────────────────────────────────────────────────

    public static String hashPassword(String plaintext) {
        return BCrypt.hashpw(plaintext, BCrypt.gensalt(10));
    }

    public static boolean checkPassword(String plaintext, String hash) {
        if (hash == null || hash.isBlank()) return false;
        try {
            return BCrypt.checkpw(plaintext, hash);
        } catch (Exception e) {
            return false;
        }
    }
}
