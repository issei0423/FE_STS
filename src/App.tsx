import { useCallback, useEffect, useRef, useState } from 'react';
import './App.css';
import { Sidebar } from './components/Sidebar';
import { MainContent } from './components/MainContent';
import { Login } from './components/Login';
import { ServerWakeupOverlay } from './components/ServerWakeupOverlay';
import { useServerWakeup } from './hooks/useServerWakeup';
import type { User } from './types';
import {
  api,
  ApiError,
  API_BASE_URL,
  configureSession,
  type ApiUser,
  type LoginResponse,
  type RosterEntry,
  type RosterStatus,
} from './api/client';

const TOKEN_STORAGE_KEY = 'fe-sts:token';
const REFRESH_TOKEN_STORAGE_KEY = 'fe-sts:refresh-token';
const DEV_AVATAR_MARK = '🛠️';
const ROSTER_POLL_MS = 15000;

const ROSTER_STATUS_MAP: Record<RosterStatus, User['status']> = {
  STUDYING: 'studying',
  ONLINE: 'online',
  OFFLINE: 'offline',
};

function readStored(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeStored(key: string, value: string | null) {
  try {
    if (value) {
      localStorage.setItem(key, value);
    } else {
      localStorage.removeItem(key);
    }
  } catch {
    // localStorage may be unavailable (private browsing, quota exceeded, policy) — continue in-memory only
  }
}

function App() {
  const { status: wakeupStatus, elapsedSeconds } = useServerWakeup();
  const [token, setToken] = useState<string | null>(null);
  const [apiUser, setApiUser] = useState<ApiUser | null>(null);
  const [bootstrapping, setBootstrapping] = useState(true);
  const [verifyError, setVerifyError] = useState('');
  const [isStudying, setIsStudying] = useState(false);
  const [studyMinutes, setStudyMinutes] = useState(0);
  const [roster, setRoster] = useState<RosterEntry[]>([]);
  // リフレッシュトークンは描画に使わないので state ではなく ref で持つ(再描画不要・常に最新値)。
  const refreshTokenRef = useRef<string | null>(null);
  const rememberRef = useRef(false);

  // ログイン状態の保存。remember が false のときはメモリ上だけに持つ(docs/07 §6)。
  const persistSession = useCallback(
    (accessToken: string | null, refreshToken: string | null, remember: boolean) => {
      rememberRef.current = remember;
      refreshTokenRef.current = refreshToken;
      setToken(accessToken);

      const persistable = remember && accessToken && refreshToken;
      writeStored(TOKEN_STORAGE_KEY, persistable ? accessToken : null);
      writeStored(REFRESH_TOKEN_STORAGE_KEY, persistable ? refreshToken : null);
    },
    []
  );

  // アクセストークン(有効期限1時間)が切れても、リフレッシュトークンがあれば
  // ユーザー操作なしにセッションを継続する。api クライアントが401を受けたときに
  // ここから再発行し、失敗したらログイン画面へ戻す。
  useEffect(() => {
    configureSession({
      getRefreshToken: () => refreshTokenRef.current,
      onRefreshed: (session) => {
        persistSession(session.accessToken, session.refreshToken, rememberRef.current);
        setApiUser(session.user);
      },
      onExpired: () => {
        persistSession(null, null, false);
        setApiUser(null);
      },
    });
    return () => configureSession(null);
  }, [persistSession]);

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
          persistSession(res.accessToken, res.refreshToken, true);
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

    const stored = readStored(TOKEN_STORAGE_KEY);
    if (!stored) {
      setBootstrapping(false);
      return;
    }

    // 復元時点でリフレッシュトークンも読み込んでおく。api.me が401になった場合は
    // クライアント側が自動でこれを使って再発行する。
    refreshTokenRef.current = readStored(REFRESH_TOKEN_STORAGE_KEY);
    rememberRef.current = true;

    api
      .me(stored)
      .then((user) => {
        // 401→自動再発行を経由した場合は、その新しいトークンを上書きしないようにする
        setToken((current) => current ?? stored);
        setApiUser(user);
      })
      .catch(() => {
        persistSession(null, null, false);
      })
      .finally(() => setBootstrapping(false));
  }, [persistSession]);

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

  const handleAuthenticated = (session: LoginResponse, remember: boolean) => {
    persistSession(session.accessToken, session.refreshToken, remember);
    setApiUser(session.user);
  };

  const handleLogout = () => {
    // サーバー側のリフレッシュトークンも失効させる(呼ばないと最大14日間有効なまま残る)。
    const refreshToken = refreshTokenRef.current;
    if (refreshToken) {
      api.logout(refreshToken).catch(() => {
        // 失効に失敗してもローカルのログアウトは進める
      });
    }
    persistSession(null, null, false);
    setApiUser(null);
  };

  const handleAvatarChange = async (file: File) => {
    if (!token || !apiUser) return;
    // レスポンスが更新後のプロフィール(新しい iconUrl)を返すので、それをそのまま採用する。
    setApiUser(await api.uploadIcon(token, file));
  };

  if (wakeupStatus === 'waking') {
    return <ServerWakeupOverlay elapsedSeconds={elapsedSeconds} />;
  }

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
  // 累計勉強時間はロースターAPIが自分の分も正しく返しているので、それを引き継ぐ。
  // ここで0を入れるとランキングで自分だけ常に0.0hの最下位になる。
  const myRosterEntry = roster.find((entry) => entry.id === apiUser.id);
  const currentUser: User = {
    id: String(apiUser.id),
    name: fullName,
    initials: apiUser.role === 'DEVELOPER' ? DEV_AVATAR_MARK : apiUser.lastName,
    // iconUrl はAPI側の相対パスなので、他ユーザー(下の roster)と同じくオリジンを付ける。
    avatarUrl: apiUser.iconUrl ? `${API_BASE_URL}${apiUser.iconUrl}` : undefined,
    // ステータスと経過分だけは即時反映したいのでローカル状態を優先する。
    status: isStudying ? 'studying' : 'online',
    currentSessionMinutes: isStudying ? studyMinutes : 0,
    totalStudyHours: myRosterEntry?.totalStudyHours ?? 0,
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
