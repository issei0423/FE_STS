package com.fests.dto;

import java.time.OffsetDateTime;

public record StudySessionResponse(
    Long id,
    OffsetDateTime startedAt,
    OffsetDateTime endedAt,
    Integer durationSec,
    boolean running,
    long todayTotalSec
) {
}
