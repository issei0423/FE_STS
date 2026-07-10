package com.fests.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ブラウザを閉じる/回線切断等でハートビートが途絶えたまま、あるいは
 * 上限時間を超えたまま「実行中」になっているセッションを定期的に自動終了する。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudySessionCleanupScheduler {

    private final StudySessionService studySessionService;

    @Scheduled(fixedDelayString = "${app.study-session.cleanup-interval-ms:30000}")
    public void cleanup() {
        int closed = studySessionService.closeStaleSessions();
        if (closed > 0) {
            log.info("自動終了したセッション数: {}", closed);
        }
    }
}
