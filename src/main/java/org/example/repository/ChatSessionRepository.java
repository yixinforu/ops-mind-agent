package org.example.repository;

import org.example.entity.ChatSessionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 会话元数据仓储
 */
@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, Long> {

    Optional<ChatSessionEntity> findByUserIdAndSessionId(Long userId, String sessionId);

    Optional<ChatSessionEntity> findByUserIdAndSessionIdAndDeletedFalse(Long userId, String sessionId);

    List<ChatSessionEntity> findByUserIdAndDeletedFalseOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    List<ChatSessionEntity> findByUserIdAndSessionIdInAndDeletedFalse(Long userId, Collection<String> sessionIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ChatSessionEntity s where s.userId = :userId and s.sessionId = :sessionId")
    Optional<ChatSessionEntity> findForUpdate(@Param("userId") Long userId, @Param("sessionId") String sessionId);
}
