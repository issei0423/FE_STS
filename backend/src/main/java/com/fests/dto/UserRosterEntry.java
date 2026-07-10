package com.fests.dto;

/** status: "STUDYING" | "ONLINE" | "OFFLINE" */
public record UserRosterEntry(
    Long id,
    String lastName,
    String firstName,
    String iconUrl,
    String status,
    int currentSessionMinutes,
    double totalStudyHours
) {
}
