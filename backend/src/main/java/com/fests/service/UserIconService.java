package com.fests.service;

import com.fests.entity.User;
import com.fests.entity.UserIcon;
import com.fests.exception.ApiException;
import com.fests.repository.UserIconRepository;
import com.fests.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RIFF_SIGNATURE = {'R', 'I', 'F', 'F'};

    private final UserIconRepository userIconRepository;
    private final UserRepository userRepository;

    public record IconFile(byte[] data, String mimeType) {
    }

    public void upload(User user, MultipartFile file) {
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "画像ファイルを選択してください");
        }

        try {
            byte[] data = file.getBytes();
            // Content-Type ヘッダは送信側の自己申告なので信用しない。中身(マジックバイト)から
            // 判定し、許可した形式でなければ弾く。スクリプト入りSVGを image/svg+xml として
            // 保存・配信できてしまう問題への対処(issue #31)
            String mimeType = detectAllowedMimeType(data);

            UserIcon icon = userIconRepository.findByUser(user).orElseGet(UserIcon::new);
            icon.setUser(user);
            icon.setFileName(file.getOriginalFilename());
            icon.setMimeType(mimeType);
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

    /**
     * 画像バイナリの先頭から形式を判定し、許可した形式の MIME タイプを返す。
     * 判定できない・許可外の場合は 400 で弾く。
     */
    private static String detectAllowedMimeType(byte[] data) {
        if (startsWith(data, PNG_SIGNATURE)) {
            return MediaType.IMAGE_PNG_VALUE;
        }
        if (startsWith(data, JPEG_SIGNATURE)) {
            return MediaType.IMAGE_JPEG_VALUE;
        }
        if (isWebp(data)) {
            return "image/webp";
        }
        throw new ApiException(
            HttpStatus.BAD_REQUEST, "INVALID_FILE_TYPE", "PNG・JPEG・WebP形式の画像のみアップロードできます");
    }

    private static boolean startsWith(byte[] data, byte[] signature) {
        if (data.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (data[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    /** WebPは "RIFF" + 4バイトのサイズ + "WEBP" という構造になっている。 */
    private static boolean isWebp(byte[] data) {
        return data.length >= 12
            && startsWith(data, RIFF_SIGNATURE)
            && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
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
