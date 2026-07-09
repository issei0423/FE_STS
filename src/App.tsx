import { useState } from 'react';
import './App.css';
import { Sidebar } from './components/Sidebar';
import { MainContent } from './components/MainContent';
import { Login } from './components/Login';
import { mockUsers } from './mockData';
import type { User } from './types';

const AUTH_STORAGE_KEY = 'fe-sts:auth';
const ACCOUNTS_STORAGE_KEY = 'fe-sts:accounts';
const DEV_AVATAR_MARK = '🛠️';

interface StoredAuth {
  lastName: string;
  firstName: string;
  avatarUrl?: string;
  isDeveloper?: boolean;
}

interface Account {
  email: string;
  lastName: string;
  firstName: string;
}

function readStoredAuth(): StoredAuth | null {
  try {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    return typeof parsed?.lastName === 'string' &&
      parsed.lastName.trim() &&
      typeof parsed?.firstName === 'string' &&
      parsed.firstName.trim()
      ? parsed
      : null;
  } catch {
    return null;
  }
}

function persistAuth(value: StoredAuth | null) {
  try {
    if (value) {
      localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(value));
    } else {
      localStorage.removeItem(AUTH_STORAGE_KEY);
    }
  } catch {
    // localStorage may be unavailable (private browsing, quota exceeded, policy) — continue in-memory only
  }
}

function readAccounts(): Account[] {
  try {
    const raw = localStorage.getItem(ACCOUNTS_STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

// Registered on サインイン (name + email + one-time password) so ログイン
// (email + password only) has something to look up on a later visit.
function upsertAccount(account: Account) {
  try {
    const accounts = readAccounts().filter(
      (a) => a.email.toLowerCase() !== account.email.toLowerCase()
    );
    accounts.push(account);
    localStorage.setItem(ACCOUNTS_STORAGE_KEY, JSON.stringify(accounts));
  } catch {
    // localStorage may be unavailable — the account just won't be found on next login
  }
}

function findAccount(email: string): Account | undefined {
  return readAccounts().find((a) => a.email.toLowerCase() === email.toLowerCase());
}

function App() {
  const [auth, setAuth] = useState<StoredAuth | null>(() => readStoredAuth());
  const [rememberMe, setRememberMe] = useState<boolean>(() => readStoredAuth() !== null);

  // ログイン: email + password only, for an account that already exists
  // (created previously via サインイン). No real backend, so "password" is
  // just required to be non-empty — the actual gate is the accounts directory.
  const handleLogin = (email: string, remember: boolean): boolean => {
    const account = findAccount(email);
    if (!account) return false;
    const newAuth: StoredAuth = { lastName: account.lastName, firstName: account.firstName };
    persistAuth(remember ? newAuth : null);
    setRememberMe(remember);
    setAuth(newAuth);
    return true;
  };

  // サインイン: full registration (name + email + one-time password).
  const handleSignIn = (lastName: string, firstName: string, email: string, remember: boolean) => {
    upsertAccount({ email, lastName, firstName });
    const newAuth: StoredAuth = { lastName, firstName };
    persistAuth(remember ? newAuth : null);
    setRememberMe(remember);
    setAuth(newAuth);
  };

  // 開発者専用の瞬間ログイン: メール/パスワード不要、名前は "- -"、保存もしない。
  const handleDevLogin = () => {
    setRememberMe(false);
    setAuth({ lastName: '-', firstName: '-', isDeveloper: true });
  };

  const handleLogout = () => {
    persistAuth(null);
    setAuth(null);
    setRememberMe(false);
  };

  const handleAvatarChange = (avatarUrl: string | undefined) => {
    setAuth((prev) => {
      if (!prev) return prev;
      const next = { ...prev, avatarUrl };
      if (rememberMe) persistAuth(next);
      return next;
    });
  };

  if (!auth) {
    return <Login onLogin={handleLogin} onSignIn={handleSignIn} onDevLogin={handleDevLogin} />;
  }

  // The logged-in person takes the "current user" slot with fresh stats (not
  // mockUsers[0]'s borrowed name/status/hours). Any mock entry sharing the same
  // name is dropped so the roster never shows a duplicate. The default icon is
  // the surname (苗字) unless a custom avatar image was uploaded, except for the
  // developer shortcut account which always shows a fixed developer mark.
  const fullName = `${auth.lastName} ${auth.firstName}`;
  const currentUser: User = {
    ...mockUsers[0],
    name: fullName,
    initials: auth.isDeveloper ? DEV_AVATAR_MARK : auth.lastName,
    avatarUrl: auth.avatarUrl,
    status: 'online',
    currentSessionMinutes: 0,
    totalStudyHours: 0,
    subject: undefined,
  };
  const users = [currentUser, ...mockUsers.slice(1).filter((u) => u.name !== fullName)];

  return (
    <div className="app-layout" id="app-layout">
      <MainContent currentUser={currentUser} />
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
