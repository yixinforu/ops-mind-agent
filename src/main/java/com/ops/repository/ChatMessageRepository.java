package com.ops.repository;

import com.ops.entity.ChatMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 会话消息仓储
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    List<ChatMessageEntity> findByUserIdAndSessionIdAndDeletedFalseOrderBySeqNoAsc(Long userId, String sessionId);

    List<ChatMessageEntity> findByUserIdAndSessionIdAndDeletedFalseOrderBySeqNoDesc(
            Long userId, String sessionId, Pageable pageable
    );

    @Query("select coalesce(max(m.seqNo), 0) from ChatMessageEntity m " +
            "where m.userId = :userId and m.sessionId = :sessionId")
    Integer findMaxSeqNo(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    @Modifying
    @Query("update ChatMessageEntity m set m.deleted = true " +
            "where m.userId = :userId and m.sessionId = :sessionId and m.deleted = false")
    int softDeleteByUserIdAndSessionId(@Param("userId") Long userId, @Param("sessionId") String sessionId);
}
