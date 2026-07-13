package com.fests.repository;

import com.fests.entity.StudySession;
import com.fests.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

    List<StudySession> findByUserAndDate(User user, LocalDate date);

    Optional<StudySession> findFirstByUserAndEndedAtIsNullOrderByStartedAtDesc(User user);

    List<StudySession> findByEndedAtIsNull();

    @Query(
        "select s from StudySession s " +
        "where s.endedAt is null and s.lastHeartbeatAt >= :threshold"
    )
    List<StudySession> findActiveSince(LocalDateTime threshold);

    @Query(
        "select coalesce(sum(s.durationSec), 0) from StudySession s " +
        "where s.user = :user and s.date = :date and s.durationSec is not null"
    )
    long sumDurationSecByUserAndDate(User user, LocalDate date);

    @Query(
        "select s.user.id as userId, coalesce(sum(s.durationSec), 0) as totalSec " +
        "from StudySession s where s.durationSec is not null group by s.user.id"
    )
    List<UserTotalSecProjection> sumDurationSecGroupByUser();

    interface UserTotalSecProjection {
        Long getUserId();
        Long getTotalSec();
    }
}
