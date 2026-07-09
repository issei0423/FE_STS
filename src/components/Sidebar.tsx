import { useRef, useState, type ChangeEvent } from 'react';
import type { User } from '../types';

interface SidebarProps {
  users: User[];
  currentUser: User;
  onLogout: () => void;
  onAvatarChange: (avatarUrl: string | undefined) => void;
}

type SidePageView = 'list' | 'ranking';

const AVATAR_MAX_SIZE = 256;

function readImageAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as string);
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

// Downscale to keep the avatar small enough for localStorage (a phone photo
// can be several MB, which would blow past the storage quota otherwise).
function resizeAvatar(dataUrl: string, maxSize = AVATAR_MAX_SIZE): Promise<string> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      const scale = Math.min(1, maxSize / Math.max(img.width, img.height));
      const canvas = document.createElement('canvas');
      canvas.width = Math.round(img.width * scale);
      canvas.height = Math.round(img.height * scale);
      const ctx = canvas.getContext('2d');
      if (!ctx) {
        reject(new Error('canvas 2d context unavailable'));
        return;
      }
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
      resolve(canvas.toDataURL('image/jpeg', 0.85));
    };
    img.onerror = () => reject(new Error('failed to decode image'));
    img.src = dataUrl;
  });
}

export function Sidebar({ users, currentUser, onLogout, onAvatarChange }: SidebarProps) {
  const [view, setView] = useState<SidePageView>('list');
  const [searchQuery, setSearchQuery] = useState('');
  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);
  const avatarInputRef = useRef<HTMLInputElement>(null);

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

  // Ranking is cumulative total only
  const rankedUsers = [...users].sort((a, b) => b.totalStudyHours - a.totalStudyHours);

  const getRankingClass = (index: number): string => {
    if (index === 0) return 'top-1';
    if (index === 1) return 'top-2';
    if (index === 2) return 'top-3';
    return 'other';
  };

  const formatSessionTime = (minutes: number): string => {
    if (minutes === 0) return '';
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    if (h > 0) return `${h}h ${m}m`;
    return `${m}m`;
  };

  const rankingStatusLabel = (user: User): string => {
    if (user.status === 'studying') return user.subject ? `📖 ${user.subject}` : '📖 勉強中';
    if (user.status === 'online') return 'オンライン';
    return 'オフライン';
  };

  const handleAvatarFileChange = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;

    try {
      const rawDataUrl = await readImageAsDataUrl(file);
      const resized = await resizeAvatar(rawDataUrl);
      onAvatarChange(resized);
    } catch {
      // Unreadable/undecodable image — leave the current avatar unchanged.
    }
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

      {/* View Switch */}
      <div className="sidepage-tabs">
        <button
          className={`sidepage-tab ${view === 'list' ? 'active' : ''}`}
          onClick={() => setView('list')}
          id="sidepage-tab-list"
        >
          ユーザー一覧
        </button>
        <button
          className={`sidepage-tab ${view === 'ranking' ? 'active' : ''}`}
          onClick={() => setView('ranking')}
          id="sidepage-tab-ranking"
        >
          ランキング
        </button>
      </div>

      {view === 'list' ? (
        <>
          {/* Search */}
          <div className="sidebar-search">
            <div className="search-input-wrapper">
              <span className="search-icon">🔍</span>
              <input
                id="user-search"
                className="text-input search-input"
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
                  <div className="user-avatar-img">
                    {user.avatarUrl ? (
                      <img className="avatar-image" src={user.avatarUrl} alt="" />
                    ) : (
                      user.initials
                    )}
                  </div>
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
        </>
      ) : (
        /* Ranking (cumulative total) */
        <div className="user-list" id="ranking-list">
          {rankedUsers.map((user, index) => (
            <div key={user.id} className="ranking-item" id={`ranking-item-${user.id}`}>
              <div className={`ranking-position ${getRankingClass(index)}`}>{index + 1}</div>
              <div className="ranking-avatar">
                {user.avatarUrl ? (
                  <img className="avatar-image" src={user.avatarUrl} alt="" />
                ) : (
                  user.initials
                )}
              </div>
              <div className="ranking-info">
                <div className="ranking-name">{user.name}</div>
                <div className="ranking-total">{rankingStatusLabel(user)}</div>
              </div>
              <div className="ranking-hours">
                {user.totalStudyHours.toFixed(1)}
                <span>h</span>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Footer - Current User */}
      <div className="sidebar-footer">
        <div className="sidebar-footer-user" id="current-user">
          <button
            className="sidebar-footer-avatar avatar-editable"
            id="avatar-upload-btn"
            aria-label="アイコン画像を変更"
            title="クリックして画像を変更"
            onClick={() => avatarInputRef.current?.click()}
          >
            {currentUser.avatarUrl ? (
              <img className="avatar-image" src={currentUser.avatarUrl} alt="" />
            ) : (
              currentUser.initials
            )}
            <span className="avatar-edit-overlay">📷</span>
          </button>
          <input
            ref={avatarInputRef}
            type="file"
            accept="image/*"
            className="avatar-file-input"
            onChange={handleAvatarFileChange}
          />
          <div className="sidebar-footer-info">
            <div className="sidebar-footer-name">{currentUser.name}</div>
            <div className="sidebar-footer-status">● オンライン</div>
          </div>
          <button className="sidebar-footer-settings" id="settings-btn" aria-label="設定">
            ⚙️
          </button>
          <button
            className="sidebar-footer-settings"
            id="logout-btn"
            aria-label="ログアウト"
            onClick={() => setShowLogoutConfirm(true)}
          >
            🚪
          </button>
        </div>
      </div>

      {showLogoutConfirm && (
        <div
          className="modal-overlay"
          role="presentation"
          onClick={() => setShowLogoutConfirm(false)}
        >
          <div
            className="modal-card"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="logout-confirm-title"
            onClick={(e) => e.stopPropagation()}
          >
            <h2 id="logout-confirm-title" className="modal-title">
              ログアウトしますか？
            </h2>
            <p className="modal-body">
              再度利用するにはメールアドレスとパスワード、またはサインインが必要になります。
            </p>
            <div className="modal-actions">
              <button
                className="btn btn-secondary"
                id="logout-cancel-btn"
                onClick={() => setShowLogoutConfirm(false)}
              >
                キャンセル
              </button>
              <button
                className="btn btn-danger"
                id="logout-confirm-btn"
                onClick={() => {
                  setShowLogoutConfirm(false);
                  onLogout();
                }}
              >
                ログアウト
              </button>
            </div>
          </div>
        </div>
      )}
    </aside>
  );
}
