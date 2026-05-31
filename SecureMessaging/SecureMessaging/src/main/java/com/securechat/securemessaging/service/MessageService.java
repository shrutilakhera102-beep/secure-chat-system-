package com.securechat.securemessaging.service;

import com.securechat.securemessaging.model.Message;
import com.securechat.securemessaging.repository.MessageRepository;
import com.securechat.securemessaging.security.AESUtil;
import com.securechat.securemessaging.security.HMACUtil;
import com.securechat.securemessaging.security.ImageEncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final MessageRepository messageRepository;
    private final SessionKeyService sessionKeyService;

    public MessageService(MessageRepository messageRepository,
                          SessionKeyService sessionKeyService) {
        this.messageRepository = messageRepository;
        this.sessionKeyService = sessionKeyService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Send text message
    // ─────────────────────────────────────────────────────────────────────────

    public Message sendMessage(String sender, String receiver, String content) {
        if (content == null || content.isBlank()) {
            throw new RuntimeException("Message content cannot be empty");
        }

        byte[] key = sessionKeyService.getOrCreateKey(sender, receiver);

        Message message = new Message();
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setContentType("TEXT");

        try {
            String encrypted = AESUtil.encrypt(content, key);
            message.setContent(encrypted);
            message.setNonce(generateUniqueNonce());
            message.setHmac(HMACUtil.generateHMAC(encrypted, key));
        } catch (Exception e) {
            log.error("Encryption failed for message from {} to {}: {}", sender, receiver, e.getMessage());
            throw new RuntimeException("Encryption failed");
        }

        return messageRepository.save(message);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Send image message
    // ─────────────────────────────────────────────────────────────────────────

    public Message sendImageMessage(String sender, String receiver, MultipartFile image) throws IOException {
        byte[] key = sessionKeyService.getOrCreateKey(sender, receiver);

        Message message = new Message();
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setContentType("IMAGE");
        message.setMimeType(image.getContentType());

        try {
            byte[] imageBytes = image.getBytes();
            String encrypted = ImageEncryptionUtil.encryptImage(imageBytes, key);
            message.setContent(encrypted);
            message.setNonce(generateUniqueNonce());
            message.setHmac(HMACUtil.generateHMAC(encrypted, key));
        } catch (Exception e) {
            log.error("Image encryption failed from {} to {}: {}", sender, receiver, e.getMessage());
            throw new RuntimeException("Image encryption failed: " + e.getMessage());
        }

        return messageRepository.save(message);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fetch full conversation between two users (used by chat page)
    // ─────────────────────────────────────────────────────────────────────────

    public List<Message> getConversation(String user1, String user2) {
        List<Message> sent = messageRepository
                .findBySenderAndReceiverOrderByTimestampAsc(user1, user2);
        List<Message> received = messageRepository
                .findBySenderAndReceiverOrderByTimestampAsc(user2, user1);

        List<Message> all = new ArrayList<>();
        all.addAll(sent);
        all.addAll(received);
        all.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));

        byte[] key = sessionKeyService.getOrCreateKey(user1, user2);
        all.forEach(msg -> decryptMessageInPlace(msg, key));

        return all;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fetch messages for a single receiver (legacy endpoint)
    // ─────────────────────────────────────────────────────────────────────────

    public List<Message> getMessages(String receiver) {
        List<Message> messages = messageRepository.findByReceiver(receiver);
        // We don't have the sender context here, so we skip decryption for
        // messages that have a proper HMAC (they require the session key).
        // The /chat endpoint should be used for full conversations.
        return messages;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Decrypt a message in-place. Skips legacy messages (null HMAC/nonce).
     * Logs and leaves content encrypted if verification or decryption fails.
     */
    private void decryptMessageInPlace(Message msg, byte[] key) {
        // Legacy messages without HMAC/nonce — leave as-is
        if (msg.getHmac() == null || msg.getNonce() == null) {
            return;
        }

        try {
            boolean valid = HMACUtil.verifyHMAC(msg.getContent(), key, msg.getHmac());
            if (!valid) {
                log.warn("HMAC integrity check failed for message id={}", msg.getId());
                msg.setContent("[integrity check failed]");
                return;
            }

            String decrypted;
            if ("IMAGE".equals(msg.getContentType())) {
                byte[] imageBytes = ImageEncryptionUtil.decryptImage(msg.getContent(), key);
                decrypted = Base64.getEncoder().encodeToString(imageBytes);
            } else {
                decrypted = AESUtil.decrypt(msg.getContent(), key);
            }
            msg.setContent(decrypted);

        } catch (Exception e) {
            log.error("Decryption failed for message id={}: {}", msg.getId(), e.getMessage());
            msg.setContent("[decryption failed]");
        }
    }

    /** Generate a UUID nonce that is guaranteed unique in the messages table. */
    private String generateUniqueNonce() {
        String nonce = UUID.randomUUID().toString();
        while (messageRepository.existsByNonce(nonce)) {
            nonce = UUID.randomUUID().toString();
        }
        return nonce;
    }
}
