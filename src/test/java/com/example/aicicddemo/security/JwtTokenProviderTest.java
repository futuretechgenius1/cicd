package com.example.aicicddemo.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private final String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private final long expirationMs = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(secret, expirationMs);
    }

    @Test
    @DisplayName("Should generate valid token and extract email")
    void generateToken_And_ExtractEmail() {
        String email = "john@example.com";
        String token = jwtTokenProvider.generateToken(email);

        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals(email, jwtTokenProvider.getEmailFromToken(token));
    }

    @Test
    @DisplayName("Should return false for invalid token")
    void validateToken_Invalid() {
        assertFalse(jwtTokenProvider.validateToken("invalid.jwt.token"));
    }
}
