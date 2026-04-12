package com.ops.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话消息实体
 */
@Entity
@Table(name = "chat_message",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_chat_message_user_session_seq", columnNames = {"user_id", "session_id", "seq_no"})
        },
        indexes = {
                @Index(name = "idx_chat_message_user_session_deleted_seq", columnList = "user_id,session_id,deleted,seq_no")
        })
@Data
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "seq_no", nullable = false)
    private Integer seqNo;

    @Column(name = "role", nullable = false, length = 16)
    private String role;

    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.deleted == null) {
            this.deleted = false;
        }
        if (this.seqNo == null) {
            this.seqNo = 0;
        }
    }
}
