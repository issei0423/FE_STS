import { useCallback, useEffect, useState } from 'react';
import './App.css';
import { Sidebar } from './components/Sidebar';
import { MainContent } from './components/MainContent';
import { Login } from './components/Login';
import type { User } from './types';
import { api, ApiError, API_BASE_URL, type ApiUser, type RosterEntry, type RosterStatus } from './api/client';

const TOKEN_STORAGE_KEY = 'fe-sts:token';
const DEV_AVATAR_MARK = '🛠️';
const ROSTER_POLL_MS = 15000;

const ROSTER_STATUS_MAP: Record<RosterStatus, User['status']> = {
  STUDYING: 'studying',
  ONLINE: 'online',
  OFFLINE: 'offline',
};

function readStoredToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_STORAGE_KEY);
  } catch {
    return null;
  }
}

function persistToken(token: string | null) {
  try {
    if (token) {
      localStorage.setItem(TOKEN_STORAGE_KEY, token);
    } else {
      localStorage.removeItem(TOKEN_STORAGE_KEY);
    }
  } catch {
    // localStorage may be unavailable (private browsing, quota exceeded, policy) — continue in-memory only
  }
}

function App() {
  const [token, setToken] = useState<string | null>(null);
  const [apiUser, setApiUser] = useState<ApiUser | null>(null);
  const [bootstrapping, setBootstrapping] = useState(true);
  const [verifyError, setVerifyError] = useState('');
  const [isStudying, setIsStudying] = useState(false);
  const [studyMinutes, setStudyMinutes] = useState(0);
  const [roster, setRoster] = useState<RosterEntry[]>([]);

  // MainContent がタイマーの実行状態を教えてくれるたびに、サイドページの
  // 自分のステータス(オンライン/勉強中)へ反映する。
  const handleStudyingChange = useCallback((studying: boolean, minutes: number) => {
    setIsStudying(studying);
    setStudyMinutes(minutes);
  }, []);

  // アプリ起動時の一度だけ: (1) メール内の確認リンク(?token=...)を処理するか、
  // (2) 記憶されたトークンでセッションを復元する。
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const linkToken = params.get('token');

    if (linkToken) {
      api
        .verify(linkToken)
        .then((res) => {
          persistToken(res.accessToken);
          setToken(res.accessToken);
          setApiUser(res.user);
        })
        .catch((err) => {
          setVerifyError(err instanceof ApiError ? err.message : '確認リンクが無効です');
        })
        .finally(() => {
          window.history.replaceState({}, '', window.location.pathname);
          setBootstrapping(false);
        });
      return;
    }

    const stored = readStoredToken();
    if (!stored) {
      setBootstrapping(false);
      return;
    }

    api
      .me(stored)
      .then((user) => {
        setToken(stored);
        setApiUser(user);
      })
      .catch(() => {
        persistToken(null);
      })
      .finally(() => setBootstrapping(false));
  }, []);

  // ログイン中は、他のユーザーの一覧・ランキング(オンライン/勉強中/オフライン)を
  // 定期的に取得して同級生の様子をほぼリアルタイムに反映する。
  useEffect(() => {
    if (!token) {
      setRoster([]);
      return;
    }

    let cancelled = false;
    const fetchRoster = () => {
      api
        .listUsers(token)
        .then((entries) => {
          if (!cancelled) setRoster(entries);
        })
        .catch(() => {
          // 開発者ログインなどユーザーが永続化されていない場合は空のまま
        });
    };

    fetchRoster();
    const interval = window.setInterval(fetchRoster, ROSTER_POLL_MS);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [token]);

  const handleAuthenticated = (accessToken: string, user: ApiUser, remember: boolean) => {
    persistToken(remember ? accessToken : null);
    setToken(accessToken);
    setApiUser(user);
  };

  const handleLogout = () => {
    persistToken(null);
    setToken(null);
    setApiUser(null);
  };

  const handleAvatarChange = async (file: File) => {
    if (!token || !apiUser) return;
    await api.uploadIcon(token, file);
    setApiUser({ ...apiUser, iconUrl: `${api.iconUrl(apiUser.id)}?t=${Date.now()}` });
  };

  if (bootstrapping) {
    return <div className="app-bootstrapping">読み込み中…</div>;
  }

  if (!token || !apiUser) {
    return <Login onAuthenticated={handleAuthenticated} verifyError={verifyError} />;
  }

  // The default icon is the surname (苗字) unless a custom avatar image was
  // uploaded, except for the developer shortcut account which always shows a
  // fixed developer mark.
  const fullName = `${apiUser.lastName} ${apiUser.firstName}`;
  const currentUser: User = {
    id: String(apiUser.id),
    name: fullName,
    initials: apiUser.role === 'DEVELOPER' ? DEV_AVATAR_MARK : apiUser.lastName,
    avatarUrl: apiUser.iconUrl ?? undefined,
    status: isStudying ? 'studying' : 'online',
    currentSessionMinutes: isStudying ? studyMinutes : 0,
    totalStudyHours: 0,
    subject: undefined,
  };

  // 自分自身は上記のローカル状態(即時反映)を優先し、他ユーザーは定期取得した
  // ロースターから表示する。
  const otherUsers: User[] = roster
    .filter((entry) => entry.id !== apiUser.id)
    .map((entry) => ({
      id: String(entry.id),
      name: `${entry.lastName} ${entry.firstName}`,
      initials: entry.lastName,
      avatarUrl: entry.iconUrl ? `${API_BASE_URL}${entry.iconUrl}` : undefined,
      status: ROSTER_STATUS_MAP[entry.status],
      currentSessionMinutes: entry.currentSessionMinutes,
      totalStudyHours: entry.totalStudyHours,
      subject: undefined,
    }));
  const users = [currentUser, ...otherUsers];

  return (
    <div className="app-layout" id="app-layout">
      <MainContent currentUser={currentUser} token={token} onStudyingChange={handleStudyingChange} />
      <Sidebar
        users={users}
        currentUser={currentUser}
        onLogout={handleLogout}
        onAvatarChange={handleAvatarChange}
      />
    </div>
  );
}

export default App;
