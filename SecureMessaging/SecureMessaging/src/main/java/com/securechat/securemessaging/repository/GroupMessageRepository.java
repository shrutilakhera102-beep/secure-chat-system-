package com.securechat.securemessaging.repository;

import com.securechat.securemessaging.model.GroupMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupMessageRepository extends JpaRepository<GroupMessage, Long> {

    List<GroupMessage> findByGroupIdOrderByTimestampAsc(Long groupId);

    boolean existsByNonce(String nonce);
}
