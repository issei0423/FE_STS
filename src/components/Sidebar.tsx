import { useState } from 'react';
import type { User } from '../types';

interface SidebarProps {
  users: User[];
  currentUser: User;
}

export function Sidebar({ users, currentUser }: SidebarProps) {
  const [searchQuery, setSearchQuery] = useState('');

  const onlineCount = users.filter(u => u.status !== 'offline').length;
  const studyingCount = users.filter(u => u.status === 'studying').length;

  const filteredUsers = users.filter(u =>
    u.name.toLowerCase().includes(searchQuery.toLowerCase())
  );

  // Sort: studying first, then online, then offline
  const sortedUsers = [...filteredUsers].sort((a, b) => {
    const statusOrder = { studying: 0, online: 1, offline: 2 };
    return statusOrder[a.status] - statusOrder[b.status];
  });

  const formatSessionTime = (minutes: number): string => {
    if (minutes === 0) return '';
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    if (h > 0) return `${h}h ${m}m`;
    return `${m}m`;
  };

  return (
    <aside className="sidebar" id="sidebar">
      {/* Header */}
      <div className="sidebar-header">
        <div className="sidebar-logo">
          <div className="sidebar-logo-icon">F</div>
          <span className="sidebar-logo-text">FE_STS</span>
        </div>
        <div className="sidebar-subtitle">基本情報 勉強時間共有</div>
      </div>

      {/* Search */}
      <div className="sidebar-search">
        <div className="search-input-wrapper">
          <span className="search-icon">🔍</span>
          <input
            id="user-search"
            className="search-input"
            type="text"
            placeholder="ユーザーを検索..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>
      </div>

      {/* Online Count */}
      <div className="online-count">
        <span className="online-count-label">
          <span className="online-dot" />
          オンライン — {onlineCount}人 (勉強中 {studyingCount}人)
        </span>
      </div>

      {/* User List */}
      <div className="user-list" id="user-list">
        {sortedUsers.map((user) => (
          <div
            key={user.id}
            className={`user-item ${user.status === 'studying' ? 'is-studying' : ''}`}
            id={`user-${user.id}`}
          >
            <div className="user-avatar">
              <div className="user-avatar-img">{user.initials}</div>
              <div className={`user-status-indicator ${user.status}`} />
            </div>
            <div className="user-info">
              <div className="user-name">{user.name}</div>
              <div className={`user-study-status ${user.status === 'studying' ? 'active' : ''}`}>
                {user.status === 'studying' && user.subject && (
                  <>📖 {user.subject}</>
                )}
                {user.status === 'online' && 'オンライン'}
                {user.status === 'offline' && 'オフライン'}
              </div>
            </div>
            {user.status === 'studying' && user.currentSessionMinutes > 0 && (
              <span className="user-time-badge">
                {formatSessionTime(user.currentSessionMinutes)}
              </span>
            )}
          </div>
        ))}
      </div>

      {/* Footer - Current User */}
      <div className="sidebar-footer">
        <div className="sidebar-footer-user" id="current-user">
          <div className="sidebar-footer-avatar">{currentUser.initials}</div>
          <div className="sidebar-footer-info">
            <div className="sidebar-footer-name">{currentUser.name}</div>
            <div className="sidebar-footer-status">● オンライン</div>
          </div>
          <button className="sidebar-footer-settings" id="settings-btn" aria-label="設定">
            ⚙️
          </button>
        </div>
      </div>
    </aside>
  );
}
