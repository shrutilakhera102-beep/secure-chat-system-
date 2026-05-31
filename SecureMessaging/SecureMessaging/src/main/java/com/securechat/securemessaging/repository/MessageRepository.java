package com.securechat.securemessaging.repository;

import com.securechat.securemessaging.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Integer> {

    List<Message> findByReceiver(String receiver);

    List<Message> findBySenderAndReceiver(String sender, String receiver);

    List<Message> findBySenderAndReceiverOrderByTimestampAsc(
            String sender,
            String receiver);

    boolean existsByNonce(String nonce);
}
