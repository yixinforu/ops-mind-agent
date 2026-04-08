package org.example.repository;

import org.example.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户账号仓储
 */
@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    @Query(value = "SELECT * FROM users WHERE BINARY username = :username LIMIT 1", nativeQuery = true)
    Optional<UserAccount> findByUsernameExact(@Param("username") String username);

    @Query(value = "SELECT COUNT(1) FROM users WHERE BINARY username = :username", nativeQuery = true)
    long countByUsernameExact(@Param("username") String username);
}
