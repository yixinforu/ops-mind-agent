package com.ops.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话元数据实体
 */
@Entity
@Table(name = "chat_session",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_chat_session_user_session", columnNames = {"user_id", "session_id"})
        },
        indexes = {
                @Index(name = "idx_chat_session_user_deleted_updated", columnList = "user_id,deleted,updated_at")
        })
@Data
public class ChatSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "title", nullable = false, length = 255)
    private String title = "新对话";

    @Column(name = "message_count", nullable = false)
    private Integer messageCount = 0;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.deleted == null) {
            this.deleted = false;
        }
        if (this.messageCount == null) {
            this.messageCount = 0;
        }
        if (this.title == null || this.title.isBlank()) {
            this.title = "新对话";
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
