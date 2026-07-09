import { useState, useEffect, useCallback, useRef } from 'react';
import type { User } from '../types';
import { api, ApiError } from '../api/client';

interface MainContentProps {
  currentUser: User;
  token: string;
}

export function MainContent({ currentUser, token }: MainContentProps) {
  const [isRunning, setIsRunning] = useState(false);
  const [runningStartedAt, setRunningStartedAt] = useState<Date | null>(null);
  const [completedTotalSec, setCompletedTotalSec] = useState(0);
  const [liveElapsedSec, setLiveElapsedSec] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const intervalRef = useRef<number | null>(null);

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

  const formatTimer = useCallback((totalSeconds: number): string => {
    const h = Math.floor(totalSeconds / 3600);
    const m = Math.floor((totalSeconds % 3600) / 60);
    const s = totalSeconds % 60;
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }, []);

  const handleStartStop = async () => {
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      if (isRunning) {
        const res = await api.stopSession(token);
        setIsRunning(false);
        setRunningStartedAt(null);
        setLiveElapsedSec(0);
        setCompletedTotalSec(res.todayTotalSec);
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

  const todayTotalHours = ((completedTotalSec + (isRunning ? liveElapsedSec : 0)) / 3600).toFixed(1);

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
            本日の合計 <strong>{todayTotalHours}</strong>h
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
