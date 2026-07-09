package com.fests.controller;

import com.fests.dto.StudySessionResponse;
import com.fests.entity.User;
import com.fests.security.CurrentUserResolver;
import com.fests.service.StudySessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/study-sessions")
@RequiredArgsConstructor
public class StudySessionController {

    private final StudySessionService studySessionService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping("/start")
    public ResponseEntity<StudySessionResponse> start(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(studySessionService.start(user));
    }

    @PostMapping("/stop")
    public ResponseEntity<StudySessionResponse> stop(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(studySessionService.stop(user));
    }

    @GetMapping("/today")
    public ResponseEntity<StudySessionResponse> today(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(studySessionService.todayStatus(user));
    }
}
