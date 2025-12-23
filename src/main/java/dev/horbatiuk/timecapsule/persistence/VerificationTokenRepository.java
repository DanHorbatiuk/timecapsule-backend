package dev.horbatiuk.timecapsule.persistence;

import dev.horbatiuk.timecapsule.persistence.entities.User;
import dev.horbatiuk.timecapsule.persistence.entities.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, User> {
    Optional<VerificationToken> findVerificationTokenByUser(User user);
    Optional<VerificationToken> findVerificationTokenByToken(UUID token);
}
