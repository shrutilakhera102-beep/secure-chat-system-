package com.securechat.securemessaging.model;

import jakarta.persistence.*;

@Entity
@Table(
    name = "session_keys",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user1", "user2"})
)
public class SessionKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String user1;

    @Column(nullable = false)
    private String user2;

    @Lob
    @Column(nullable = false)
    private String sessionKey;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUser1() { return user1; }
    public void setUser1(String user1) { this.user1 = user1; }

    public String getUser2() { return user2; }
    public void setUser2(String user2) { this.user2 = user2; }

    public String getSessionKey() { return sessionKey; }
    public void setSessionKey(String sessionKey) { this.sessionKey = sessionKey; }
}
