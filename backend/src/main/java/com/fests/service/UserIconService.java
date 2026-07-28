package com.fests.service;

import com.fests.entity.User;
import com.fests.entity.UserIcon;
import com.fests.exception.ApiException;
import com.fests.repository.UserIconRepository;
import com.fests.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class UserIconService {

    private final UserIconRepository userIconRepository;
    private final UserRepository userRepository;

    public record IconFile(byte[] data, String mimeType) {
    }

    public void upload(User user, MultipartFile file) {
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "画像ファイルを選択してください");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", "画像ファイルのみアップロードできます");
        }

        try {
            byte[] data = file.getBytes();

            UserIcon icon = userIconRepository.findByUser(user).orElseGet(UserIcon::new);
            icon.setUser(user);
            icon.setFileName(file.getOriginalFilename());
            icon.setMimeType(contentType);
            icon.setImageData(data);
            userIconRepository.save(icon);

            // CurrentUserResolver が返す User は open-in-view: false のためトランザクション外で
            // ロードされた detached エンティティで、setter だけでは UPDATE が飛ばない。
            // save() で明示的に merge しないと icon_path が NULL のままになる(issue: アイコンが
            // 他ユーザーから見えない・再ログインで消える)。
            user.setIconPath("user-" + user.getId());
            userRepository.save(user);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD_FAILED", "アップロードに失敗しました");
        }
    }

    @Transactional(readOnly = true)
    public IconFile getIcon(Long userId) {
        UserIcon icon = userIconRepository.findByUserId(userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ICON_NOT_FOUND", "アイコンが見つかりません"));

        return new IconFile(icon.getImageData(), icon.getMimeType());
    }

    /** アイコン画像のURL。未登録なら null。 */
    @Transactional(readOnly = true)
    public String iconUrl(Long userId) {
        return userIconRepository.findUpdatedAtByUserId(userId)
            .map(updatedAt -> iconUrl(userId, updatedAt))
            .orElse(null);
    }

    /** ロースター用に全ユーザー分のアイコンURLをまとめて引く(アイコン未登録のユーザーは含まない)。 */
    @Transactional(readOnly = true)
    public Map<Long, String> iconUrls() {
        return userIconRepository.findAllVersions().stream()
            .collect(Collectors.toMap(
                UserIconRepository.IconVersion::getUserId,
                v -> iconUrl(v.getUserId(), v.getUpdatedAt())));
    }

    /**
     * 更新時刻をクエリパラメータに載せることで、アイコンを差し替えた瞬間にURLが変わり、
     * 閲覧側のブラウザキャッシュに古い画像が残らないようにする。
     */
    private static String iconUrl(Long userId, LocalDateTime updatedAt) {
        return "/api/users/" + userId + "/icon?v=" + updatedAt.toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}
