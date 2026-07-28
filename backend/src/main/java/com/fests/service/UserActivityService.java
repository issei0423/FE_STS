package com.fests.service;

import com.fests.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JWT認証済みリクエストのたびに呼ばれ、オンライン/オフライン判定に使うタイムスタンプを更新する。
 *
 * ロースターのポーリング(15秒)と計測中のハートビート(30秒)により、ログイン中の
 * ユーザー1人あたり毎分6回程度のリクエストが来る。毎回UPDATEすると書き込み回数が
 * ユーザー数に比例して膨らみ、同一行の更新競合も起きるため、前回更新から
 * app.presence.touch-throttle-seconds 経過した場合だけ実際にUPDATEする(issue #30)。
 *
 * 間引き間隔はオンライン判定のしきい値(app.presence.online-threshold-minutes)より
 * 十分短くしておくこと。そうでないと、オンラインなのにオフラインと判定されうる。
 */
@Service
@RequiredArgsConstructor
public class UserActivityService {

    private final UserRepository userRepository;

    /** userId -> 最後に実際にUPDATEしたエポック秒。単一インスタンス前提のインメモリ実装。 */
    private final Map<Long, Long> lastTouchedEpochSec = new ConcurrentHashMap<>();

    @Value("${app.presence.touch-throttle-seconds}")
    private long touchThrottleSeconds;

    @Transactional
    public void touch(Long userId) {
        if (userId == null || userId <= 0) {
            return; // 開発者ログインなど永続化されていない仮想ユーザーは対象外
        }

        long nowSec = System.currentTimeMillis() / 1000;
        Long previous = lastTouchedEpochSec.get(userId);
        if (previous != null && nowSec - previous < touchThrottleSeconds) {
            return;
        }
        // 先に記録してから更新する。並行リクエストが同時に通り抜けても、
        // 余分に走るUPDATEはたかだか数回で、値は同じ方向にしか進まない。
        lastTouchedEpochSec.put(userId, nowSec);

        userRepository.touchLastSeen(userId, LocalDateTime.now());
    }
}
