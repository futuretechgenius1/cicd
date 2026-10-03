package com.example.aicicddemo.controller;

import com.example.aicicddemo.dto.AuthResponse;
import com.example.aicicddemo.dto.LoginRequest;
import com.example.aicicddemo.dto.RegisterRequest;
import com.example.aicicddemo.dto.UserResponse;
import com.example.aicicddemo.exception.DuplicateEmailException;
import com.example.aicicddemo.exception.InvalidCredentialsException;
import com.example.aicicddemo.security.CustomUserDetailsService;
import com.example.aicicddemo.security.JwtTokenProvider;
import com.example.aicicddemo.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("POST /api/auth/register should return 201 Created")
    void register_Success() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .name("John Doe")
                .email("john@example.com")
                .password("Password@123")
                .build();

        UserResponse response = UserResponse.builder()
                .id(1L)
                .name("John Doe")
                .email("john@example.com")
                .build();

        when(userService.registerUser(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    @DisplayName("POST /api/auth/register should return 400 Bad Request on invalid payload")
    void register_ValidationError() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .name("")
                .email("invalid-email")
                .password("123")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/auth/register should return 409 Conflict when email exists")
    void register_DuplicateEmail() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .name("John Doe")
                .email("john@example.com")
                .password("Password@123")
                .build();

        when(userService.registerUser(any(RegisterRequest.class)))
                .thenThrow(new DuplicateEmailException("Email address is already in use"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_EMAIL"));
    }

    @Test
    @DisplayName("POST /api/auth/login should return 200 OK with token")
    void login_Success() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("john@example.com")
                .password("Password@123")
                .build();

        AuthResponse response = AuthResponse.builder()
                .accessToken("mock-jwt-token")
                .tokenType("Bearer")
                .build();

        when(userService.loginUser(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock-jwt-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("POST /api/auth/login should return 401 Unauthorized on invalid credentials")
    void login_InvalidCredentials() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("john@example.com")
                .password("WrongPassword")
                .build();

        when(userService.loginUser(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }
}
