package org.hrcopilot.persistence.repository;

import java.util.Optional;
import java.util.UUID;
import org.hrcopilot.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByUsername(String username);
}
