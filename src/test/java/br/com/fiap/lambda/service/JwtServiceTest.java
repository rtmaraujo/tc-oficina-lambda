package br.com.fiap.lambda.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "teste-secret-para-jwt-com-mais-de-32-caracteres";

    @Test
    @DisplayName("deve gerar token JWT valido com subject e status")
    void deveGerarTokenValido() {
        JwtService jwtService = new JwtService(SECRET);
        String token = jwtService.generateToken("12345678909", "ATIVO");

        assertNotNull(token);

        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("12345678909", claims.getSubject());
        assertEquals("ATIVO", claims.get("status", String.class));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    @DisplayName("deve gerar token com expiracao de 24 horas")
    void deveGerarTokenComExpiracaoDe24Horas() {
        JwtService jwtService = new JwtService(SECRET);
        long before = System.currentTimeMillis();
        String token = jwtService.generateToken("52998224725", "ATIVO");
        long after = System.currentTimeMillis();

        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date expiration = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();

        long expectedMin = before + 86400000L - 1000L;
        long expectedMax = after + 86400000L + 1000L;
        assertTrue(expiration.getTime() >= expectedMin && expiration.getTime() <= expectedMax,
                "expiracao deveria ser ~24h apos a geracao");
    }
}
