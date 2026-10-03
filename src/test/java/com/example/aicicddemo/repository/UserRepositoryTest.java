package com.example.aicicddemo.repository;

import com.example.aicicddemo.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        sampleUser = User.builder()
                .name("John Doe")
                .email("john@example.com")
                .password("hashedPassword123")
                .build();
    }

    @Test
    @DisplayName("Should save user and find by email")
    void findByEmail_Success() {
        userRepository.save(sampleUser);

        Optional<User> found = userRepository.findByEmail("john@example.com");

        assertTrue(found.isPresent());
        assertEquals("John Doe", found.get().getName());
    }

    @Test
    @DisplayName("Should return true for existsByEmail when email exists")
    void existsByEmail_True() {
        userRepository.save(sampleUser);

        boolean exists = userRepository.existsByEmail("john@example.com");

        assertTrue(exists);
    }

    @Test
    @DisplayName("Should return false for existsByEmail when email does not exist")
    void existsByEmail_False() {
        boolean exists = userRepository.existsByEmail("nonexistent@example.com");

        assertFalse(exists);
    }
}
