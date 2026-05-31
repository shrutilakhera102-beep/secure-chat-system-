package com.securechat.securemessaging.service;

import com.securechat.securemessaging.model.User;
import com.securechat.securemessaging.repository.UserRepository;
import com.securechat.securemessaging.security.DHUtil;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.KeyPair;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Register a new user.
     * Generates a DH key pair and persists both public and private keys.
     */
    public User registerUser(String username, String password) {
        if (username == null || username.isBlank()) {
            throw new RuntimeException("Username cannot be empty");
        }
        if (password == null || password.length() < 6) {
            throw new RuntimeException("Password must be at least 6 characters");
        }
        if (userRepository.findByUsername(username) != null) {
            throw new RuntimeException("User already exists");
        }

        User user = new User();
        user.setUsername(username.trim());
        user.setPasswordHash(encoder.encode(password));

        try {
            KeyPair keyPair = DHUtil.generateKeyPair();
            // Persist both keys so DH works across server restarts
            user.setPublicKey(DHUtil.publicKeyToString(keyPair.getPublic()));
            user.setPrivateKey(DHUtil.privateKeyToString(keyPair.getPrivate()));
        } catch (Exception e) {
            log.error("DH key generation failed for user {}: {}", username, e.getMessage());
            throw new RuntimeException("Key generation failed during registration");
        }

        return userRepository.save(user);
    }

    /**
     * Authenticate a user and return the User entity.
     * The caller is responsible for issuing a JWT token.
     */
    public User loginUser(String username, String password) {
        if (username == null || password == null) {
            throw new RuntimeException("Username and password are required");
        }

        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("Invalid password");
        }

        return user;
    }

    /** Fetch the DH public key for a given username. */
    public String getPublicKey(String username) {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        return user.getPublicKey();
    }
}
