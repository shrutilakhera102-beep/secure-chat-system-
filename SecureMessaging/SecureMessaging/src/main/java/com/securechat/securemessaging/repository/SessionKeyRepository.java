package com.securechat.securemessaging.repository;

import com.securechat.securemessaging.model.SessionKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionKeyRepository extends JpaRepository<SessionKey, Long> {

    /**
     * Find a session key by the canonical (alphabetically ordered) user pair.
     * SessionKeyService always stores keys with user1 <= user2.
     */
    SessionKey findByUser1AndUser2(String user1, String user2);
}
