package com.frauddetection.online.repository;

import com.frauddetection.online.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    Optional<UserAccount> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);
}
