package com.fests.service;

import com.fests.dto.StudySessionResponse;
import com.fests.entity.StudySession;
import com.fests.entity.User;
import com.fests.exception.ApiException;
import com.fests.repository.StudySessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class StudySessionService {

    private final StudySessionRepository studySessionRepository;

    @Value("${app.study-session.heartbeat-timeout-seconds}")
    private int heartbeatTimeoutSeconds;

    @Value("${app.study-session.max-duration-hours}")
    private int maxDurationHours;

    public StudySessionResponse start(User user) {
        studySessionRepository.findFirstByUserAndEndedAtIsNullOrderByStartedAtDesc(user)
            .ifPresent(s -> {
                throw new ApiException(HttpStatus.CONFLICT, "SESSION_ALREADY_RUNNING", "既に計測中のセッションがあります");
            });

        LocalDateTime now = LocalDateTime.now();
        StudySession session = new StudySession();
        session.setUser(user);
        session.setStartedAt(now);
        session.setDate(LocalDate.now());
        session.setLastHeartbeatAt(now);
        studySessionRepository.save(session);

        return toResponse(session, user);
    }

    public StudySessionResponse stop(User user) {
        StudySession session = studySessionRepository
            .findFirstByUserAndEndedAtIsNullOrderByStartedAtDesc(user)
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "NO_RUNNING_SESSION", "計測中のセッションがありません"));

        closeAt(session, LocalDateTime.now());
        return toResponse(session, user);
    }

    /** クライアントが計測中に定期送信するハートビート。最終活動時刻を更新する。 */
    public StudySessionResponse heartbeat(User user) {
        StudySession session = studySessionRepository
            .findFirstByUserAndEndedAtIsNullOrderByStartedAtDesc(user)
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "NO_RUNNING_SESSION", "計測中のセッションがありません"));

        session.setLastHeartbeatAt(LocalDateTime.now());
        studySessionRepository.save(session);
        return toResponse(session, user);
    }

    @Transactional(readOnly = true)
    public StudySessionResponse todayStatus(User user) {
        Optional<StudySession> running =
            studySessionRepository.findFirstByUserAndEndedAtIsNullOrderByStartedAtDesc(user);
        return toResponse(running.orElse(null), user);
    }

    /**
     * ハートビートが一定時間途絶えた、または1セッションの上限時間を超えたまま
     * 実行中になっているセッションを自動終了する(ブラウザを閉じた/放置した場合の対策)。
     *
     * @return 自動終了したセッション数
     */
    public int closeStaleSessions() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime heartbeatDeadline = now.minusSeconds(heartbeatTimeoutSeconds);
        List<StudySession> running = studySessionRepository.findByEndedAtIsNull();

        int closed = 0;
        for (StudySession session : running) {
            LocalDateTime maxDeadline = session.getStartedAt().plusHours(maxDurationHours);
            if (!now.isBefore(maxDeadline)) {
                closeAt(session, maxDeadline);
                closed++;
                continue;
            }

            LocalDateTime lastActivity =
                session.getLastHeartbeatAt() != null ? session.getLastHeartbeatAt() : session.getStartedAt();
            if (lastActivity.isBefore(heartbeatDeadline)) {
                closeAt(session, lastActivity);
                closed++;
            }
        }
        return closed;
    }

    private void closeAt(StudySession session, LocalDateTime endedAt) {
        session.setEndedAt(endedAt);
        session.setDurationSec((int) Duration.between(session.getStartedAt(), endedAt).getSeconds());
        studySessionRepository.save(session);
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
