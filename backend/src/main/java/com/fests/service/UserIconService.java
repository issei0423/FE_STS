package com.fests.service;

import com.fests.entity.User;
import com.fests.entity.UserIcon;
import com.fests.exception.ApiException;
import com.fests.repository.UserIconRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@Transactional
@RequiredArgsConstructor
public class UserIconService {

    private final UserIconRepository userIconRepository;

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

            user.setIconPath("user-" + user.getId());
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
}
