package com.fests.service;

import com.fests.dto.UserRosterEntry;
import com.fests.entity.StudySession;
import com.fests.entity.User;
import com.fests.repository.StudySessionRepository;
import com.fests.repository.StudySessionRepository.UserTotalSecProjection;
import com.fests.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserRosterService {

    private final UserRepository userRepository;
    private final StudySessionRepository studySessionRepository;
    private final UserIconService userIconService;

    @Value("${app.presence.online-threshold-minutes}")
    private int onlineThresholdMinutes;

    @Value("${app.study-session.heartbeat-timeout-seconds}")
    private int heartbeatTimeoutSeconds;

    @Transactional(readOnly = true)
    public List<UserRosterEntry> listRoster() {
        List<User> users = userRepository.findAll();
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime heartbeatDeadline = now.minusSeconds(heartbeatTimeoutSeconds);
        Map<Long, StudySession> runningByUserId = studySessionRepository.findActiveSince(heartbeatDeadline).stream()
            .collect(Collectors.toMap(s -> s.getUser().getId(), Function.identity(), (a, b) -> a));

        Map<Long, Long> totalSecByUserId = studySessionRepository.sumDurationSecGroupByUser().stream()
            .collect(Collectors.toMap(UserTotalSecProjection::getUserId, UserTotalSecProjection::getTotalSec));

        LocalDateTime onlineThreshold = now.minusMinutes(onlineThresholdMinutes);

        // アイコンURLは users.icon_path ではなく user_icons(実データ)から引く。更新時刻が
        // URLに乗るので、誰かが画像を差し替えたら次回のロースター取得で自動的に新URLになる。
        Map<Long, String> iconUrlByUserId = userIconService.iconUrls();

        return users.stream()
            .map(user -> toEntry(
                user, runningByUserId.get(user.getId()), totalSecByUserId, iconUrlByUserId, now, onlineThreshold))
            .toList();
    }

    private UserRosterEntry toEntry(
        User user,
        StudySession running,
        Map<Long, Long> totalSecByUserId,
        Map<Long, String> iconUrlByUserId,
        LocalDateTime now,
        LocalDateTime onlineThreshold
    ) {
        long totalSec = totalSecByUserId.getOrDefault(user.getId(), 0L);
        double totalHours = totalSec / 3600.0;
        String iconUrl = iconUrlByUserId.get(user.getId());

        if (running != null) {
            int minutes = (int) Duration.between(running.getStartedAt(), now).toMinutes();
            return new UserRosterEntry(
                user.getId(), user.getLastName(), user.getFirstName(), iconUrl, "STUDYING", minutes, totalHours);
        }

        boolean online = user.getLastSeenAt() != null && user.getLastSeenAt().isAfter(onlineThreshold);
        return new UserRosterEntry(
            user.getId(), user.getLastName(), user.getFirstName(), iconUrl,
            online ? "ONLINE" : "OFFLINE", 0, totalHours);
    }
}
