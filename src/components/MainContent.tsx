import { useState, useEffect, useCallback, useRef } from 'react';
import type { User } from '../types';
import { api, ApiError, API_BASE_URL, type StudySessionResponse } from '../api/client';

interface MainContentProps {
  currentUser: User;
  token: string;
  onStudyingChange: (isStudying: boolean, elapsedMinutes: number) => void;
}

// docs/07_open_design_decisions.md §1-2 で決定した値
const HEARTBEAT_INTERVAL_MS = 30000;
const IDLE_TIMEOUT_MS = 10 * 60 * 1000;

// pagehide/visibilitychange はレスポンスを待てないため、
// keepalive フラグ付きの fire-and-forget リクエストで代替する。
function sendKeepalive(path: string, token: string) {
  try {
    void fetch(`${API_BASE_URL}${path}`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      keepalive: true,
    });
  } catch {
    // ベストエフォートのため失敗は無視
  }
}

export function MainContent({ currentUser, token, onStudyingChange }: MainContentProps) {
  const [isRunning, setIsRunning] = useState(false);
  const [runningStartedAt, setRunningStartedAt] = useState<Date | null>(null);
  const [completedTotalSec, setCompletedTotalSec] = useState(0);
  const [liveElapsedSec, setLiveElapsedSec] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const intervalRef = useRef<number | null>(null);
  const lastActivityRef = useRef<number>(Date.now());

  // 起動時に本日の状態(計測中かどうか・本日の合計)をサーバーから復元する。
  useEffect(() => {
    api
      .todaySession(token)
      .then((res) => {
        if (res.running && res.startedAt) {
          const startedAt = new Date(res.startedAt);
          const elapsedNow = Math.max(0, Math.floor((Date.now() - startedAt.getTime()) / 1000));
          setRunningStartedAt(startedAt);
          setIsRunning(true);
          setCompletedTotalSec(Math.max(0, res.todayTotalSec - elapsedNow));
          setLiveElapsedSec(elapsedNow);
        } else {
          setCompletedTotalSec(res.todayTotalSec);
        }
      })
      .catch(() => {
        // 開発者ログインなどユーザーが永続化されていない場合は 0 からのローカル表示にフォールバック
      });
  }, [token]);

  // 計測中は毎秒、開始時刻からの経過時間を再計算する(ドリフト防止のため setInterval で単純加算しない)。
  useEffect(() => {
    if (isRunning && runningStartedAt) {
      intervalRef.current = window.setInterval(() => {
        setLiveElapsedSec(Math.max(0, Math.floor((Date.now() - runningStartedAt.getTime()) / 1000)));
      }, 1000);
    } else if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, [isRunning, runningStartedAt]);

  // サイドページの自分のステータス(オンライン/勉強中)に反映してもらうため、
  // 実行状態と経過時間(分)を親コンポーネントへ伝える。
  useEffect(() => {
    onStudyingChange(isRunning, Math.floor(liveElapsedSec / 60));
  }, [isRunning, liveElapsedSec, onStudyingChange]);

  const stopLocally = useCallback((res: StudySessionResponse) => {
    setIsRunning(false);
    setRunningStartedAt(null);
    setLiveElapsedSec(0);
    setCompletedTotalSec(res.todayTotalSec);
  }, []);

  // ユーザー操作(マウス/キーボード/タッチ/スクロール)を検知し、放置検知の基準時刻を更新する。
  useEffect(() => {
    const markActive = () => {
      lastActivityRef.current = Date.now();
    };
    const events: Array<keyof WindowEventMap> = ['mousemove', 'mousedown', 'keydown', 'touchstart', 'scroll'];
    events.forEach((evt) => window.addEventListener(evt, markActive, { passive: true }));
    return () => {
      events.forEach((evt) => window.removeEventListener(evt, markActive));
    };
  }, []);

  // 計測中は30秒ごとにハートビートを送信する。サーバー側で既にタイムアウト等により
  // セッションが終了していた場合(NO_RUNNING_SESSION)はローカル状態も同期する。
  // また、10分以上ユーザー操作が無ければ放置とみなし自動停止する。
  useEffect(() => {
    if (!isRunning) return;

    const tick = async () => {
      // 非表示中はそもそもユーザー操作イベントが来ないため、放置判定の対象外にする。
      // (画面を伏せて参考書を読むような使い方で自動停止させないため。issue #29)
      const idleFor = document.visibilityState === 'hidden'
        ? 0
        : Date.now() - lastActivityRef.current;
      if (idleFor >= IDLE_TIMEOUT_MS) {
        try {
          const res = await api.stopSession(token);
          stopLocally(res);
        } catch {
          // ベストエフォートのため失敗は無視
        }
        return;
      }

      try {
        await api.heartbeat(token);
      } catch (err) {
        if (err instanceof ApiError && err.status === 400) {
          setIsRunning(false);
          setRunningStartedAt(null);
          setLiveElapsedSec(0);
        }
      }
    };

    const id = window.setInterval(tick, HEARTBEAT_INTERVAL_MS);
    return () => clearInterval(id);
  }, [isRunning, token, stopLocally]);

  // 離脱時に stop を送らない。beforeunload/pagehide はリロードでも発火し、iOS Safari では
  // bfcache 入りやアプリ切り替えでも pagehide が発火するため、送ってしまうと「リロードしたら
  // 計測が終わる」「アプリを切り替えたら終わる」になる(issue #29)。
  // 本当に離脱した場合はサーバー側のハートビートタイムアウト(90秒)が終了させるので、
  // ここでは離脱直前・復帰時にハートビートを送って猶予を保つだけにする。
  useEffect(() => {
    if (!isRunning) return;

    const handlePageHide = () => sendKeepalive('/api/study-sessions/heartbeat', token);
    const handleVisibility = () => {
      if (document.visibilityState === 'hidden') {
        sendKeepalive('/api/study-sessions/heartbeat', token);
      } else {
        // 復帰は操作とみなす(バックグラウンド中の時間で放置判定にしない)
        lastActivityRef.current = Date.now();
        sendKeepalive('/api/study-sessions/heartbeat', token);
      }
    };

    window.addEventListener('pagehide', handlePageHide);
    document.addEventListener('visibilitychange', handleVisibility);
    return () => {
      window.removeEventListener('pagehide', handlePageHide);
      document.removeEventListener('visibilitychange', handleVisibility);
    };
  }, [isRunning, token]);

  const formatTimer = useCallback((totalSeconds: number): string => {
    const h = Math.floor(totalSeconds / 3600);
    const m = Math.floor((totalSeconds % 3600) / 60);
    const s = totalSeconds % 60;
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }, []);

  // 小数点1桁の時間表示だと数分程度の学習が「0.0h」に丸められ、
  // 累積されていないように見えてしまうため、秒/分/時間で使い分ける。
  const formatTotalTime = useCallback((totalSeconds: number): string => {
    if (totalSeconds < 60) return `${totalSeconds}秒`;
    const totalMinutes = Math.floor(totalSeconds / 60);
    if (totalMinutes < 60) return `${totalMinutes}分`;
    const h = Math.floor(totalMinutes / 60);
    const m = totalMinutes % 60;
    return m > 0 ? `${h}時間${m}分` : `${h}時間`;
  }, []);

  const handleStartStop = async () => {
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      if (isRunning) {
        const res = await api.stopSession(token);
        stopLocally(res);
      } else {
        const res = await api.startSession(token);
        setIsRunning(true);
        setRunningStartedAt(res.startedAt ? new Date(res.startedAt) : new Date());
        setLiveElapsedSec(0);
        setCompletedTotalSec(res.todayTotalSec);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '通信に失敗しました');
    } finally {
      setBusy(false);
    }
  };

  const todayTotalDisplay = formatTotalTime(completedTotalSec + (isRunning ? liveElapsedSec : 0));

  return (
    <main className="main-content" id="main-content">
      <div className={`study-recording-overlay ${isRunning ? 'active' : ''}`} aria-hidden="true" />

      {/* Header */}
      <header className="main-header">
        <div className="main-header-left">
          <h1 className="main-header-title">📊 ダッシュボード</h1>
          <span className="main-header-badge">LIVE</span>
        </div>
        <div className="main-header-actions">
          <button className="header-action-btn" id="notifications-btn" aria-label="通知">
            🔔
          </button>
          <button className="header-action-btn" id="fullscreen-btn" aria-label="フルスクリーン">
            ⛶
          </button>
        </div>
      </header>

      {/* Body */}
      <div className="main-body">
        {/* Timer Section */}
        <div className="timer-section fade-in-up" id="timer-section">
          <div className="timer-label">
            {isRunning ? '⏳ 勉強中...' : '⏱️ 勉強タイマー'}
          </div>
          <div className={`timer-display ${isRunning ? 'is-running' : ''}`} id="timer-display">
            {formatTimer(liveElapsedSec)}
          </div>
          <div className="timer-subject">
            {currentUser.subject
              ? `科目: ${currentUser.subject}`
              : 'スタートボタンで勉強を開始しましょう'}
          </div>
          <div className="timer-today-total" id="timer-today-total">
            本日の合計 <strong>{todayTotalDisplay}</strong>
          </div>

          {error && <p className="login-error">{error}</p>}

          <div className="timer-actions">
            <button
              className={`btn btn-primary ${isRunning ? 'is-running' : ''}`}
              id="timer-start-btn"
              onClick={handleStartStop}
              disabled={busy}
            >
              {isRunning ? '⏸ 停止' : '▶ スタート'}
            </button>
          </div>
        </div>
      </div>
    </main>
  );
}
