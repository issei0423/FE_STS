package com.fests.service;

import com.fests.dto.StudySessionResponse;
import com.fests.entity.StudySession;
import com.fests.entity.User;
import com.fests.exception.ApiException;
import com.fests.repository.StudySessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class StudySessionService {

    private final StudySessionRepository studySessionRepository;

    public StudySessionResponse start(User user) {
        studySessionRepository.findFirstByUserAndEndedAtIsNullOrderByStartedAtDesc(user)
            .ifPresent(s -> {
                throw new ApiException(HttpStatus.CONFLICT, "SESSION_ALREADY_RUNNING", "既に計測中のセッションがあります");
            });

        StudySession session = new StudySession();
        session.setUser(user);
        session.setStartedAt(LocalDateTime.now());
        session.setDate(LocalDate.now());
        studySessionRepository.save(session);

        return toResponse(session, user);
    }

    public StudySessionResponse stop(User user) {
        StudySession session = studySessionRepository
            .findFirstByUserAndEndedAtIsNullOrderByStartedAtDesc(user)
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "NO_RUNNING_SESSION", "計測中のセッションがありません"));

        LocalDateTime now = LocalDateTime.now();
        session.setEndedAt(now);
        session.setDurationSec((int) Duration.between(session.getStartedAt(), now).getSeconds());
        studySessionRepository.save(session);

        return toResponse(session, user);
    }

    @Transactional(readOnly = true)
    public StudySessionResponse todayStatus(User user) {
        Optional<StudySession> running =
            studySessionRepository.findFirstByUserAndEndedAtIsNullOrderByStartedAtDesc(user);
        return toResponse(running.orElse(null), user);
    }

    private StudySessionResponse toResponse(StudySession session, User user) {
        long completedTotal = studySessionRepository.sumDurationSecByUserAndDate(user, LocalDate.now());

        if (session == null) {
            return new StudySessionResponse(null, null, null, null, false, completedTotal);
        }

        boolean running = session.isRunning();
        long liveTotal = completedTotal;
        if (running) {
            liveTotal += Duration.between(session.getStartedAt(), LocalDateTime.now()).getSeconds();
        }

        return new StudySessionResponse(
            session.getId(),
            session.getStartedAt(),
            session.getEndedAt(),
            session.getDurationSec(),
            running,
            liveTotal
        );
    }
}
