package com.securechat.securemessaging.service;

import com.securechat.securemessaging.model.User;
import com.securechat.securemessaging.repository.UserRepository;
import com.securechat.securemessaging.security.DHUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Handles Diffie-Hellman shared secret derivation.
 *
 * Private keys are persisted in the database (Base64 PKCS8) so they
 * survive server restarts — fixing the original in-memory-only design.
 */
@Service
public class DHService {

    private static final Logger log = LoggerFactory.getLogger(DHService.class);

    private final UserRepository userRepository;

    public DHService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Derive the 16-byte shared secret between user1 and user2.
     * Uses user1's persisted private key and user2's stored public key.
     *
     * @param user1           the initiating user
     * @param user2PublicKeyStr Base64-encoded DH public key of the other user
     * @return 16-byte shared secret
     */
    public byte[] generateSharedSecret(String user1, String user2PublicKeyStr) {
        try {
            User user = userRepository.findByUsername(user1);
            if (user == null || user.getPrivateKey() == null) {
                throw new RuntimeException("Private key not found for user: " + user1);
            }

            PrivateKey privateKey = DHUtil.stringToPrivateKey(user.getPrivateKey());
            PublicKey otherPublicKey = DHUtil.stringToPublicKey(user2PublicKeyStr);

            return DHUtil.generateSharedSecret(privateKey, otherPublicKey);

        } catch (Exception e) {
            log.error("Shared secret generation failed for user {}: {}", user1, e.getMessage());
            throw new RuntimeException("Shared secret generation failed");
        }
    }
}
