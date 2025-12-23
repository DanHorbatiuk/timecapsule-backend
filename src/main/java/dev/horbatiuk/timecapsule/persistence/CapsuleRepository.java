package dev.horbatiuk.timecapsule.persistence;

import dev.horbatiuk.timecapsule.persistence.entities.Capsule;
import dev.horbatiuk.timecapsule.persistence.entities.User;
import dev.horbatiuk.timecapsule.persistence.entities.enums.CapsuleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CapsuleRepository extends JpaRepository<Capsule, UUID> {
    List<Capsule> findAllByAppUser(User user);
    List<Capsule> findAllByAppUser_Id(UUID appUserId);
    long countByAppUserEmail(String email);

    @Query("SELECT c FROM Capsule c JOIN FETCH c.appUser WHERE c.id = :id")
    Optional<Capsule> findByIdWithUser(@Param("id") UUID id);

    @Query("""
        SELECT c FROM Capsule c 
        JOIN c.appUser u
        WHERE 
          c.title = COALESCE(:title, c.title)
          AND c.status = COALESCE(:status, c.status)
          AND u.email = COALESCE(:email, u.email)
          AND u.id = COALESCE(:userId, u.id)
          AND c.createdAt >= COALESCE(:createdAfter, c.createdAt)
          AND c.createdAt <= COALESCE(:createdBefore, c.createdAt)
        """)
    Page<Capsule> findAllWithFilters(
            @Param("title") String title,
            @Param("status") CapsuleStatus status,
            @Param("email") String email,
            @Param("createdAfter") Instant createdAfter,
            @Param("createdBefore") Instant createdBefore,
            @Param("userId") UUID userId,
            Pageable pageable
    );
}
