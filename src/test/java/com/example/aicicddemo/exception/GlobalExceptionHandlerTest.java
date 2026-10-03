package com.example.aicicddemo.exception;

import com.example.aicicddemo.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(request.getRequestURI()).thenReturn("/api/test");
    }

    @Test
    @DisplayName("Should handle DuplicateEmailException")
    void handleDuplicateEmail() {
        DuplicateEmailException ex = new DuplicateEmailException("Email exists");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleDuplicateEmail(ex, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("DUPLICATE_EMAIL", response.getBody().getError());
    }

    @Test
    @DisplayName("Should handle InvalidCredentialsException")
    void handleInvalidCredentials() {
        InvalidCredentialsException ex = new InvalidCredentialsException("Invalid creds");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInvalidCredentials(ex, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_CREDENTIALS", response.getBody().getError());
    }

    @Test
    @DisplayName("Should handle ResourceNotFoundException")
    void handleNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Not found");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleNotFound(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("RESOURCE_NOT_FOUND", response.getBody().getError());
    }

    @Test
    @DisplayName("Should handle generic Exception")
    void handleGenericException() {
        Exception ex = new RuntimeException("Unexpected error");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGenericException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INTERNAL_SERVER_ERROR", response.getBody().getError());
    }
}
