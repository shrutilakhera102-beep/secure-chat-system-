package com.securechat.securemessaging.service;

import com.securechat.securemessaging.model.Group;
import com.securechat.securemessaging.model.GroupMember;
import com.securechat.securemessaging.model.GroupMessage;
import com.securechat.securemessaging.repository.GroupMemberRepository;
import com.securechat.securemessaging.repository.GroupMessageRepository;
import com.securechat.securemessaging.repository.GroupRepository;
import com.securechat.securemessaging.repository.UserRepository;
import com.securechat.securemessaging.security.AESUtil;
import com.securechat.securemessaging.security.HMACUtil;
import com.securechat.securemessaging.security.ImageEncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class GroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupService.class);

    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final GroupMessageRepository messageRepository;
    private final UserRepository userRepository;

    public GroupService(GroupRepository groupRepository,
                        GroupMemberRepository memberRepository,
                        GroupMessageRepository messageRepository,
                        UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Create group
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Create a new group. The creator is automatically added as ADMIN.
     * A fresh random 128-bit AES key is generated for the group.
     */
    @Transactional
    public Group createGroup(String groupName, String creatorUsername) {
        if (groupName == null || groupName.isBlank()) {
            throw new RuntimeException("Group name cannot be empty");
        }
        if (groupName.length() > 100) {
            throw new RuntimeException("Group name must be 100 characters or less");
        }
        if (groupRepository.existsByName(groupName.trim())) {
            throw new RuntimeException("A group with that name already exists");
        }

        // Generate a fresh random AES-128 key for this group
        String groupKey = generateGroupKey();

        Group group = new Group();
        group.setName(groupName.trim());
        group.setCreatedBy(creatorUsername);
        group.setGroupKey(groupKey);
        group = groupRepository.save(group);

        // Add creator as ADMIN member
        GroupMember admin = new GroupMember();
        admin.setGroup(group);
        admin.setUsername(creatorUsername);
        admin.setRole("ADMIN");
        memberRepository.save(admin);

        return groupRepository.findById(group.getId()).orElseThrow();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Add member
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Add a user to a group. Only ADMINs can add members.
     */
    @Transactional
    public Group addMember(Long groupId, String requesterUsername, String newMemberUsername) {
        Group group = getGroupOrThrow(groupId);
        assertAdmin(group, requesterUsername);

        if (userRepository.findByUsername(newMemberUsername) == null) {
            throw new RuntimeException("User '" + newMemberUsername + "' does not exist");
        }
        if (memberRepository.existsByGroupIdAndUsername(groupId, newMemberUsername)) {
            throw new RuntimeException("User '" + newMemberUsername + "' is already in this group");
        }

        GroupMember member = new GroupMember();
        member.setGroup(group);
        member.setUsername(newMemberUsername);
        member.setRole("MEMBER");
        memberRepository.save(member);

        return groupRepository.findById(groupId).orElseThrow();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Remove member
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Remove a member from a group.
     * ADMINs can remove anyone. Regular members can only remove themselves (leave).
     */
    @Transactional
    public Group removeMember(Long groupId, String requesterUsername, String targetUsername) {
        Group group = getGroupOrThrow(groupId);
        assertMember(group, requesterUsername);

        boolean isAdmin = isAdmin(group, requesterUsername);
        boolean isSelf  = requesterUsername.equals(targetUsername);

        if (!isAdmin && !isSelf) {
            throw new RuntimeException("Only admins can remove other members");
        }
        if (!memberRepository.existsByGroupIdAndUsername(groupId, targetUsername)) {
            throw new RuntimeException("User '" + targetUsername + "' is not in this group");
        }
        // Prevent removing the last admin
        if (isAdmin(group, targetUsername)) {
            long adminCount = group.getMembers().stream()
                    .filter(m -> "ADMIN".equals(m.getRole())).count();
            if (adminCount <= 1) {
                throw new RuntimeException("Cannot remove the last admin. Promote another member first.");
            }
        }

        memberRepository.deleteByGroupIdAndUsername(groupId, targetUsername);
        return groupRepository.findById(groupId).orElseThrow();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Promote member to admin
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public Group promoteMember(Long groupId, String requesterUsername, String targetUsername) {
        Group group = getGroupOrThrow(groupId);
        assertAdmin(group, requesterUsername);

        GroupMember member = memberRepository
                .findByGroupIdAndUsername(groupId, targetUsername)
                .orElseThrow(() -> new RuntimeException("User '" + targetUsername + "' is not in this group"));

        member.setRole("ADMIN");
        memberRepository.save(member);
        return groupRepository.findById(groupId).orElseThrow();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Get groups for a user
    // ─────────────────────────────────────────────────────────────────────────

    public List<Group> getGroupsForUser(String username) {
        return groupRepository.findGroupsByUsername(username);
    }

    public Group getGroup(Long groupId, String requesterUsername) {
        Group group = getGroupOrThrow(groupId);
        assertMember(group, requesterUsername);
        return group;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Send text message to group
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public GroupMessage sendMessage(Long groupId, String sender, String content) {
        if (content == null || content.isBlank()) {
            throw new RuntimeException("Message content cannot be empty");
        }

        Group group = getGroupOrThrow(groupId);
        assertMember(group, sender);

        byte[] key = Base64.getDecoder().decode(group.getGroupKey());

        GroupMessage msg = new GroupMessage();
        msg.setGroupId(groupId);
        msg.setSender(sender);
        msg.setContentType("TEXT");

        try {
            String encrypted = AESUtil.encrypt(content, key);
            msg.setContent(encrypted);
            msg.setNonce(generateUniqueNonce());
            msg.setHmac(HMACUtil.generateHMAC(encrypted, key));
        } catch (Exception e) {
            log.error("Group message encryption failed: {}", e.getMessage());
            throw new RuntimeException("Encryption failed");
        }

        return messageRepository.save(msg);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Send image message to group
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public GroupMessage sendImageMessage(Long groupId, String sender, MultipartFile image) throws IOException {
        Group group = getGroupOrThrow(groupId);
        assertMember(group, sender);

        byte[] key = Base64.getDecoder().decode(group.getGroupKey());

        GroupMessage msg = new GroupMessage();
        msg.setGroupId(groupId);
        msg.setSender(sender);
        msg.setContentType("IMAGE");
        msg.setMimeType(image.getContentType());

        try {
            byte[] imageBytes = image.getBytes();
            String encrypted = ImageEncryptionUtil.encryptImage(imageBytes, key);
            msg.setContent(encrypted);
            msg.setNonce(generateUniqueNonce());
            msg.setHmac(HMACUtil.generateHMAC(encrypted, key));
        } catch (Exception e) {
            log.error("Group image encryption failed: {}", e.getMessage());
            throw new RuntimeException("Image encryption failed: " + e.getMessage());
        }

        return messageRepository.save(msg);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Get messages for a group
    // ─────────────────────────────────────────────────────────────────────────

    public List<GroupMessage> getMessages(Long groupId, String requesterUsername) {
        Group group = getGroupOrThrow(groupId);
        assertMember(group, requesterUsername);

        byte[] key = Base64.getDecoder().decode(group.getGroupKey());
        List<GroupMessage> messages = messageRepository.findByGroupIdOrderByTimestampAsc(groupId);
        messages.forEach(msg -> decryptMessageInPlace(msg, key));
        return messages;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void decryptMessageInPlace(GroupMessage msg, byte[] key) {
        if (msg.getHmac() == null || msg.getNonce() == null) return;

        try {
            boolean valid = HMACUtil.verifyHMAC(msg.getContent(), key, msg.getHmac());
            if (!valid) {
                log.warn("HMAC check failed for group message id={}", msg.getId());
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
            log.error("Decryption failed for group message id={}: {}", msg.getId(), e.getMessage());
            msg.setContent("[decryption failed]");
        }
    }

    private Group getGroupOrThrow(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));
    }

    private void assertMember(Group group, String username) {
        boolean isMember = group.getMembers().stream()
                .anyMatch(m -> m.getUsername().equals(username));
        if (!isMember) {
            throw new RuntimeException("You are not a member of this group");
        }
    }

    private void assertAdmin(Group group, String username) {
        boolean isAdmin = group.getMembers().stream()
                .anyMatch(m -> m.getUsername().equals(username) && "ADMIN".equals(m.getRole()));
        if (!isAdmin) {
            throw new RuntimeException("Only group admins can perform this action");
        }
    }

    private boolean isAdmin(Group group, String username) {
        return group.getMembers().stream()
                .anyMatch(m -> m.getUsername().equals(username) && "ADMIN".equals(m.getRole()));
    }

    /** Generate a fresh random 128-bit AES key, Base64-encoded. */
    private String generateGroupKey() throws RuntimeException {
        try {
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(128);
            SecretKey key = kg.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate group key");
        }
    }

    private String generateUniqueNonce() {
        String nonce = UUID.randomUUID().toString();
        while (messageRepository.existsByNonce(nonce)) {
            nonce = UUID.randomUUID().toString();
        }
        return nonce;
    }
}
