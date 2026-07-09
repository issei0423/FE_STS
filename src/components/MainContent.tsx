import { useState, useEffect, useCallback, useRef } from 'react';
import type { User } from '../types';

interface MainContentProps {
  currentUser: User;
}

export function MainContent({ currentUser }: MainContentProps) {
  const [isRunning, setIsRunning] = useState(false);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [todayTotalSeconds, setTodayTotalSeconds] = useState(0);
  const intervalRef = useRef<number | null>(null);

  // Timer logic — today's total keeps accumulating across start/stop cycles,
  // independent of the resettable stopwatch display.
  useEffect(() => {
    if (isRunning) {
      intervalRef.current = window.setInterval(() => {
        setElapsedSeconds(prev => prev + 1);
        setTodayTotalSeconds(prev => prev + 1);
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
  }, [isRunning]);

  const formatTimer = useCallback((totalSeconds: number): string => {
    const h = Math.floor(totalSeconds / 3600);
    const m = Math.floor((totalSeconds % 3600) / 60);
    const s = totalSeconds % 60;
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }, []);

  const handleStartStop = () => {
    setIsRunning(prev => !prev);
  };

  const handleReset = () => {
    setIsRunning(false);
    setElapsedSeconds(0);
  };

  const todayTotalHours = (todayTotalSeconds / 3600).toFixed(1);

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
            {formatTimer(elapsedSeconds)}
          </div>
          <div className="timer-subject">
            {currentUser.subject
              ? `科目: ${currentUser.subject}`
              : 'スタートボタンで勉強を開始しましょう'}
          </div>
          <div className="timer-today-total" id="timer-today-total">
            本日の合計 <strong>{todayTotalHours}</strong>h
          </div>
          <div className="timer-actions">
            <button
              className={`btn btn-primary ${isRunning ? 'is-running' : ''}`}
              id="timer-start-btn"
              onClick={handleStartStop}
            >
              {isRunning ? '⏸ 停止' : '▶ スタート'}
            </button>
            <button
              className="btn btn-secondary"
              id="timer-reset-btn"
              onClick={handleReset}
            >
              ↺ リセット
            </button>
          </div>
        </div>
      </div>
    </main>
  );
}
