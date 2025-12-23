package dev.horbatiuk.timecapsule.persistence;

import dev.horbatiuk.timecapsule.persistence.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findUserByEmail(String email);
    Optional<User> findUserById(UUID id);
}
