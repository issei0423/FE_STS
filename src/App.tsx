import { useState } from 'react';
import './App.css';
import { Sidebar } from './components/Sidebar';
import { MainContent } from './components/MainContent';
import { Login } from './components/Login';
import { mockUsers } from './mockData';
import type { User } from './types';

const AUTH_STORAGE_KEY = 'fe-sts:auth';

interface StoredAuth {
  name: string;
}

function readStoredAuth(): StoredAuth | null {
  try {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    return typeof parsed?.name === 'string' && parsed.name.trim() ? parsed : null;
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

function getInitials(name: string): string {
  const parts = name.trim().split(/\s+/);
  if (parts.length >= 2) {
    return `${parts[0].charAt(0)}${parts[1].charAt(0)}`;
  }
  return name.trim().slice(0, 2);
}

function App() {
  const [auth, setAuth] = useState<StoredAuth | null>(() => readStoredAuth());

  const handleLoginSuccess = (name: string, rememberMe: boolean) => {
    persistAuth(rememberMe ? { name } : null);
    setAuth({ name });
  };

  const handleLogout = () => {
    persistAuth(null);
    setAuth(null);
  };

  if (!auth) {
    return <Login onLoginSuccess={handleLoginSuccess} />;
  }

  // The logged-in person takes the "current user" slot with fresh stats (not
  // mockUsers[0]'s borrowed name/status/hours). Any mock entry sharing the same
  // name is dropped so the roster never shows a duplicate.
  const currentUser: User = {
    ...mockUsers[0],
    name: auth.name,
    initials: getInitials(auth.name),
    status: 'online',
    currentSessionMinutes: 0,
    totalStudyHours: 0,
    subject: undefined,
  };
  const users = [currentUser, ...mockUsers.slice(1).filter((u) => u.name !== auth.name)];

  return (
    <div className="app-layout" id="app-layout">
      <Sidebar users={users} currentUser={currentUser} onLogout={handleLogout} />
      <MainContent users={users} currentUser={currentUser} />
    </div>
  );
}

export default App;
