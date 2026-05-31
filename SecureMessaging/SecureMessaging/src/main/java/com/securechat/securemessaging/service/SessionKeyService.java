package com.securechat.securemessaging.service;

import com.securechat.securemessaging.model.SessionKey;
import com.securechat.securemessaging.model.User;
import com.securechat.securemessaging.repository.SessionKeyRepository;
import com.securechat.securemessaging.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;

/**
 * Manages per-conversation AES session keys derived via Diffie-Hellman.
 *
 * Keys are stored in the database so they persist across restarts.
 * The lookup is symmetric: (A,B) and (B,A) resolve to the same key.
 */
@Service
public class SessionKeyService {

    private static final Logger log = LoggerFactory.getLogger(SessionKeyService.class);

    private final SessionKeyRepository repository;
    private final UserRepository userRepository;
    private final DHService dhService;

    public SessionKeyService(SessionKeyRepository repository,
                             UserRepository userRepository,
                             DHService dhService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.dhService = dhService;
    }

    /**
     * Return the 16-byte AES session key for the (user1, user2) pair.
     * Creates and persists a new key via DH if one does not yet exist.
     *
     * @return 16-byte raw AES key
     */
    @Transactional
    public byte[] getOrCreateKey(String user1, String user2) {
        // Canonical ordering so (A,B) and (B,A) always resolve to the same row
        String lo = user1.compareTo(user2) <= 0 ? user1 : user2;
        String hi = user1.compareTo(user2) <= 0 ? user2 : user1;

        SessionKey existing = repository.findByUser1AndUser2(lo, hi);
        if (existing != null) {
            return Base64.getDecoder().decode(existing.getSessionKey());
        }

        // Derive shared secret via DH
        User hiUser = userRepository.findByUsername(hi);
        if (hiUser == null || hiUser.getPublicKey() == null) {
            throw new RuntimeException("Cannot derive session key: public key missing for " + hi);
        }

        byte[] secret = dhService.generateSharedSecret(lo, hiUser.getPublicKey());

        SessionKey newKey = new SessionKey();
        newKey.setUser1(lo);
        newKey.setUser2(hi);
        newKey.setSessionKey(Base64.getEncoder().encodeToString(secret));
        repository.save(newKey);

        log.debug("Created new session key for ({}, {})", lo, hi);
        return secret;
    }
}
