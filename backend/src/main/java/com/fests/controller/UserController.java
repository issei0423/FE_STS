package com.fests.controller;

import com.fests.dto.UserDto;
import com.fests.dto.UserRosterEntry;
import com.fests.entity.User;
import com.fests.security.CurrentUserResolver;
import com.fests.service.UserIconService;
import com.fests.service.UserRosterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final CurrentUserResolver currentUserResolver;
    private final UserIconService userIconService;
    private final UserRosterService userRosterService;

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        return ResponseEntity.ok(UserDto.from(user, userIconService.iconUrl(user.getId())));
    }

    /** ログイン中の全ユーザーの一覧・ランキング表示用ロースター(オンライン/勉強中/オフラインを含む)。 */
    @GetMapping
    public ResponseEntity<List<UserRosterEntry>> listUsers() {
        return ResponseEntity.ok(userRosterService.listRoster());
    }

    /** 更新後のプロフィール(新しい iconUrl を含む)を返し、呼び出し側が即座に表示を差し替えられるようにする。 */
    @PostMapping(value = "/me/icon", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserDto> uploadIcon(
        Authentication authentication,
        @RequestParam("file") MultipartFile file
    ) {
        User user = currentUserResolver.resolve(authentication);
        userIconService.upload(user, file);
        return ResponseEntity.ok(UserDto.from(user, userIconService.iconUrl(user.getId())));
    }

    @GetMapping("/{id}/icon")
    public ResponseEntity<byte[]> getIcon(@PathVariable Long id) {
        UserIconService.IconFile icon = userIconService.getIcon(id);
        // URL に更新時刻(?v=)が入っており、差し替えれば別URLになるため長期キャッシュして良い。
        // 逆にURLが同じ間は中身も変わらないので、古い画像が残り続けることはない。
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(icon.mimeType()))
            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=31536000, immutable")
            .body(icon.data());
    }
}
