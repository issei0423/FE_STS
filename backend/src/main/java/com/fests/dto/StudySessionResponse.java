package com.fests.dto;

import java.time.LocalDateTime;

public record StudySessionResponse(
    Long id,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    Integer durationSec,
    boolean running,
    long todayTotalSec
) {
}
