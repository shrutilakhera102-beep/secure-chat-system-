# 👥 Group Chat Feature — Complete Guide

## Overview

SecureChat now supports **WhatsApp-style group chats** with end-to-end encryption, admin controls, and member management.

---

## Features

### ✅ Core Functionality
- **Create Groups** — Any user can create a group and becomes the admin
- **Add Members** — Admins can add any registered user to the group
- **Remove Members** — Admins can remove members; members can leave voluntarily
- **Promote to Admin** — Admins can promote members to admin role
- **Send Messages** — Text and image messages, encrypted with a shared group key
- **Real-time Updates** — 3-second polling keeps messages in sync

### 🔒 Security
- **Shared AES-128 Key** — Each group has a unique randomly-generated 128-bit AES key
- **AES-GCM Encryption** — All messages encrypted with random IV per message
- **HMAC Integrity** — Every message has an HMAC-SHA256 tag to prevent tampering
- **Nonce Protection** — UUID nonces prevent replay attacks
- **JWT Authentication** — All endpoints require a valid JWT token

### 👤 Roles
- **ADMIN** — Can add/remove members, promote members, send messages
- **MEMBER** — Can send messages, leave group

---

## Backend Architecture

### Models

**`Group`** (`chat_groups` table)
- `id` — Primary key
- `name` — Unique group name (max 100 chars)
- `createdBy` — Username of creator
- `createdAt` — Timestamp
- `groupKey` — Base64-encoded 128-bit AES key (never exposed in API)
- `members` — One-to-many relationship with `GroupMember`

**`GroupMember`** (`group_members` table)
- `id` — Primary key
- `group_id` — Foreign key to `Group`
- `username` — Member username
- `role` — "ADMIN" or "MEMBER"
- `joinedAt` — Timestamp
- Unique constraint on `(group_id, username)`

**`GroupMessage`** (`group_messages` table)
- `id` — Primary key
- `groupId` — Foreign key to `Group`
- `sender` — Username of sender
- `content` — Encrypted message (Base64)
- `contentType` — "TEXT" or "IMAGE"
- `mimeType` — Image MIME type (if IMAGE)
- `hmac` — HMAC-SHA256 of encrypted content
- `nonce` — UUID for replay protection
- `timestamp` — Message timestamp

### Service Layer

**`GroupService`**
- `createGroup(name, creator)` — Creates group with random AES key, adds creator as ADMIN
- `addMember(groupId, requester, newMember)` — Admin-only, adds user to group
- `removeMember(groupId, requester, target)` — Admin removes anyone, members can leave
- `promoteMember(groupId, requester, target)` — Admin-only, promotes member to ADMIN
- `getGroupsForUser(username)` — Returns all groups user belongs to
- `sendMessage(groupId, sender, content)` — Encrypts and saves text message
- `sendImageMessage(groupId, sender, image)` — Encrypts and saves image message
- `getMessages(groupId, requester)` — Returns decrypted messages for group

### API Endpoints

All endpoints require `Authorization: Bearer <JWT>` header.

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/groups` | Create new group |
| `GET` | `/groups` | List my groups |
| `GET` | `/groups/{id}` | Get group details |
| `POST` | `/groups/{id}/members` | Add member (admin only) |
| `DELETE` | `/groups/{id}/members/{username}` | Remove member / leave |
| `POST` | `/groups/{id}/members/{username}/promote` | Promote to admin |
| `GET` | `/groups/{id}/messages` | Get messages |
| `POST` | `/groups/{id}/messages` | Send text message |
| `POST` | `/groups/{id}/messages/image` | Send image message |

---

## Frontend UI

### Sidebar Tabs
- **💬 Chats** — Direct messages (1-on-1)
- **👥 Groups** — Group chats

### Group Panel
- **＋ New Group** button — Opens create group modal
- **My Groups** list — Shows all groups with member count

### Group Chat Header
- **👥 Members** — Opens member list modal
- **➕ Add** — Opens add member modal (admin only)
- **🚪 Leave** — Leave the group

### Modals
1. **Create Group** — Enter group name, click Create
2. **Add Member** — Enter username, click Add (admin only)
3. **Group Info** — View members, promote to admin, remove members (admin only)

---

## Usage Examples

### Create a Group
```javascript
POST /groups
Authorization: Bearer <token>
Content-Type: application/json

{ "name": "Study Group" }
```

### Add a Member
```javascript
POST /groups/1/members
Authorization: Bearer <token>
Content-Type: application/json

{ "username": "alice" }
```

### Send a Message
```javascript
POST /groups/1/messages
Authorization: Bearer <token>
Content-Type: application/json

{ "content": "Hello everyone!" }
```

### Send an Image
```javascript
POST /groups/1/messages/image
Authorization: Bearer <token>
Content-Type: multipart/form-data

image: <file>
```

---

## Security Considerations

### ✅ What's Secure
- Messages encrypted with AES-GCM (random IV per message)
- HMAC integrity verification prevents tampering
- JWT authentication prevents unauthorized access
- Nonces prevent replay attacks
- Group keys never exposed in API responses

### ⚠️ Academic Limitations
- Group keys stored in database (not end-to-end in the WhatsApp sense)
- No forward secrecy (same key for all messages)
- No key rotation
- Server can decrypt all messages (has the group key)

For a production system, consider:
- **Signal Protocol** for true E2E encryption
- **Key rotation** on member changes
- **Perfect forward secrecy** with ephemeral keys
- **Hardware security modules** for key storage

---

## Database Schema

```sql
CREATE TABLE chat_groups (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) UNIQUE NOT NULL,
    created_by VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL,
    group_key TEXT NOT NULL
);

CREATE TABLE group_members (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    username VARCHAR(50) NOT NULL,
    role VARCHAR(10) NOT NULL,
    joined_at DATETIME NOT NULL,
    UNIQUE KEY (group_id, username),
    FOREIGN KEY (group_id) REFERENCES chat_groups(id)
);

CREATE TABLE group_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    sender VARCHAR(50) NOT NULL,
    content LONGTEXT,
    content_type VARCHAR(10),
    mime_type VARCHAR(50),
    hmac VARCHAR(255),
    nonce VARCHAR(255),
    timestamp DATETIME NOT NULL,
    FOREIGN KEY (group_id) REFERENCES chat_groups(id)
);
```

---

## Testing Checklist

- [ ] Create a group
- [ ] Add 2+ members to the group
- [ ] Send text messages from different members
- [ ] Send image messages
- [ ] Promote a member to admin
- [ ] Admin removes a member
- [ ] Member leaves the group
- [ ] Verify messages are encrypted in database
- [ ] Verify HMAC integrity checks work
- [ ] Verify non-members cannot access group messages

---

## Troubleshooting

**"You are not a member of this group"**
→ You were removed or never added. Ask an admin to add you.

**"Only group admins can perform this action"**
→ You need ADMIN role. Ask an existing admin to promote you.

**"Cannot remove the last admin"**
→ Promote another member to admin first, then leave.

**Messages not appearing**
→ Check browser console for errors. Verify JWT token is valid.

**Image not displaying**
→ Check MIME type. Try PNG fallback. Max size is 5MB.

---

## Future Enhancements

- [ ] Group avatars
- [ ] Message reactions (👍 ❤️ 😂)
- [ ] @mentions
- [ ] Message search
- [ ] File attachments (PDF, docs)
- [ ] Voice messages
- [ ] Video calls
- [ ] Read receipts
- [ ] Typing indicators
- [ ] Message editing/deletion
- [ ] Group descriptions
- [ ] Invite links
- [ ] Mute notifications

---

**Built with ❤️ for SecureChat**
