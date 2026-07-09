package com.fests.repository;

import com.fests.entity.StudySession;
import com.fests.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

    List<StudySession> findByUserAndDate(User user, LocalDate date);

    Optional<StudySession> findFirstByUserAndEndedAtIsNullOrderByStartedAtDesc(User user);

    @org.springframework.data.jpa.repository.Query(
        "select coalesce(sum(s.durationSec), 0) from StudySession s " +
        "where s.user = :user and s.date = :date and s.durationSec is not null"
    )
    long sumDurationSecByUserAndDate(User user, LocalDate date);
}
