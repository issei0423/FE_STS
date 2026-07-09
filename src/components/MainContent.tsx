import { useState, useEffect, useCallback, useRef } from 'react';
import type { User, RankingPeriod } from '../types';

interface MainContentProps {
  users: User[];
  currentUser: User;
}

export function MainContent({ users, currentUser }: MainContentProps) {
  const [isRunning, setIsRunning] = useState(false);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [rankingPeriod, setRankingPeriod] = useState<RankingPeriod>('today');
  const intervalRef = useRef<number | null>(null);

  const onlineCount = users.filter(u => u.status !== 'offline').length;
  const studyingCount = users.filter(u => u.status === 'studying').length;
  const totalHoursToday = users.reduce((sum, u) => sum + u.currentSessionMinutes / 60, 0);

  // Timer logic
  useEffect(() => {
    if (isRunning) {
      intervalRef.current = window.setInterval(() => {
        setElapsedSeconds(prev => prev + 1);
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

  // Sort users by total study hours for ranking
  const rankedUsers = [...users].sort((a, b) => b.totalStudyHours - a.totalStudyHours);
  const maxHours = rankedUsers[0]?.totalStudyHours ?? 1;

  const getRankingClass = (index: number): string => {
    if (index === 0) return 'top-1';
    if (index === 1) return 'top-2';
    if (index === 2) return 'top-3';
    return 'other';
  };

  const periodLabels: Record<RankingPeriod, string> = {
    today: '今日',
    week: '今週',
    total: '累計',
  };

  return (
    <main className="main-content" id="main-content">
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
        {/* Stats Grid */}
        <div className="stats-grid fade-in-up">
          <div className="stat-card" id="stat-online">
            <div className="stat-card-icon">👥</div>
            <div className="stat-card-value">{onlineCount}</div>
            <div className="stat-card-label">オンライン</div>
          </div>
          <div className="stat-card" id="stat-studying">
            <div className="stat-card-icon">📖</div>
            <div className="stat-card-value">{studyingCount}</div>
            <div className="stat-card-label">勉強中</div>
          </div>
          <div className="stat-card" id="stat-hours">
            <div className="stat-card-icon">⏱️</div>
            <div className="stat-card-value">{totalHoursToday.toFixed(1)}</div>
            <div className="stat-card-label">本日の合計 (時間)</div>
          </div>
        </div>

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

        {/* Ranking Section */}
        <div className="ranking-section fade-in-up" id="ranking-section">
          <div className="ranking-header">
            <h2 className="ranking-title">🏆 勉強時間ランキング</h2>
            <div className="ranking-tabs">
              {(Object.keys(periodLabels) as RankingPeriod[]).map((period) => (
                <button
                  key={period}
                  className={`ranking-tab ${rankingPeriod === period ? 'active' : ''}`}
                  onClick={() => setRankingPeriod(period)}
                  id={`ranking-tab-${period}`}
                >
                  {periodLabels[period]}
                </button>
              ))}
            </div>
          </div>
          <div className="ranking-list" id="ranking-list">
            {rankedUsers.map((user, index) => (
              <div
                key={user.id}
                className="ranking-item"
                id={`ranking-item-${user.id}`}
              >
                <div className={`ranking-position ${getRankingClass(index)}`}>
                  {index + 1}
                </div>
                <div className="ranking-avatar">{user.initials}</div>
                <div className="ranking-info">
                  <div className="ranking-name">{user.name}</div>
                  <div className="ranking-total">
                    {user.status === 'studying' ? '🟡 勉強中' : '累計時間'}
                  </div>
                </div>
                <div className="ranking-progress">
                  <div
                    className="ranking-progress-fill"
                    style={{ width: `${(user.totalStudyHours / maxHours) * 100}%` }}
                  />
                </div>
                <div className="ranking-hours">
                  {user.totalStudyHours.toFixed(1)}
                  <span>h</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </main>
  );
}
