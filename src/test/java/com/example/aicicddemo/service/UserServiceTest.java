package com.example.aicicddemo.service;

import com.example.aicicddemo.dto.AuthResponse;
import com.example.aicicddemo.dto.LoginRequest;
import com.example.aicicddemo.dto.RegisterRequest;
import com.example.aicicddemo.dto.UserResponse;
import com.example.aicicddemo.entity.User;
import com.example.aicicddemo.exception.DuplicateEmailException;
import com.example.aicicddemo.exception.InvalidCredentialsException;
import com.example.aicicddemo.exception.ResourceNotFoundException;
import com.example.aicicddemo.repository.UserRepository;
import com.example.aicicddemo.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @InjectMocks
    private UserService userService;

    private User sampleUser;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .name("John Doe")
                .email("john@example.com")
                .password("hashedPassword123")
                .build();

        registerRequest = RegisterRequest.builder()
                .name("John Doe")
                .email("john@example.com")
                .password("Password@123")
                .build();

        loginRequest = LoginRequest.builder()
                .email("john@example.com")
                .password("Password@123")
                .build();
    }

    @Test
    @DisplayName("Should successfully register user")
    void registerUser_Success() {
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password@123")).thenReturn("hashedPassword123");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        UserResponse response = userService.registerUser(registerRequest);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("John Doe", response.getName());
        assertEquals("john@example.com", response.getEmail());

        verify(userRepository, times(1)).existsByEmail("john@example.com");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw DuplicateEmailException when email already exists")
    void registerUser_DuplicateEmail() {
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> userService.registerUser(registerRequest));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should successfully login user and return JWT token")
    void loginUser_Success() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("Password@123", "hashedPassword123")).thenReturn(true);
        when(tokenProvider.generateToken("john@example.com")).thenReturn("mock-jwt-token");

        AuthResponse response = userService.loginUser(loginRequest);

        assertNotNull(response);
        assertEquals("mock-jwt-token", response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
    }

    @Test
    @DisplayName("Should throw InvalidCredentialsException when email not found")
    void loginUser_UserNotFound() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> userService.loginUser(loginRequest));
    }

    @Test
    @DisplayName("Should throw InvalidCredentialsException when password does not match")
    void loginUser_WrongPassword() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("Password@123", "hashedPassword123")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> userService.loginUser(loginRequest));
    }

    @Test
    @DisplayName("Should get current user profile")
    void getCurrentUser_Success() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(sampleUser));

        UserResponse response = userService.getCurrentUser("john@example.com");

        assertNotNull(response);
        assertEquals("john@example.com", response.getEmail());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when user profile not found")
    void getCurrentUser_NotFound() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getCurrentUser("john@example.com"));
    }

    @Test
    @DisplayName("Should get user by ID")
    void getUserById_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        UserResponse response = userService.getUserById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when user ID not found")
    void getUserById_NotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getUserById(1L));
    }
}
