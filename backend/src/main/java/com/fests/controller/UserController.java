package com.fests.controller;

import com.fests.dto.MessageResponse;
import com.fests.dto.UserDto;
import com.fests.entity.User;
import com.fests.security.CurrentUserResolver;
import com.fests.service.UserIconService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final CurrentUserResolver currentUserResolver;
    private final UserIconService userIconService;

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        String iconUrl = "/api/users/" + user.getId() + "/icon";
        return ResponseEntity.ok(UserDto.from(user, user.getIconPath() != null ? iconUrl : null));
    }

    @PostMapping(value = "/me/icon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MessageResponse> uploadIcon(
        Authentication authentication,
        @RequestParam("file") MultipartFile file
    ) {
        User user = currentUserResolver.resolve(authentication);
        userIconService.upload(user, file);
        return ResponseEntity.ok(new MessageResponse("アイコンを更新しました"));
    }

    @GetMapping("/{id}/icon")
    public ResponseEntity<byte[]> getIcon(@PathVariable Long id) {
        UserIconService.IconFile icon = userIconService.getIcon(id);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(icon.mimeType()))
            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
            .body(icon.data());
    }
}
