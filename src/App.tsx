import { useState } from 'react';
import './App.css';
import { Sidebar } from './components/Sidebar';
import { MainContent } from './components/MainContent';
import { Login } from './components/Login';
import { mockUsers } from './mockData';

const AUTH_STORAGE_KEY = 'fe-sts:auth';

interface StoredAuth {
  name: string;
}

function readStoredAuth(): StoredAuth | null {
  try {
    const raw = localStorage.getItem(AUTH_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    return typeof parsed?.name === 'string' ? parsed : null;
  } catch {
    return null;
  }
}

function App() {
  const [auth, setAuth] = useState<StoredAuth | null>(() => readStoredAuth());

  const handleLoginSuccess = (name: string, rememberMe: boolean) => {
    if (rememberMe) {
      localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify({ name }));
    } else {
      localStorage.removeItem(AUTH_STORAGE_KEY);
    }
    setAuth({ name });
  };

  const handleLogout = () => {
    localStorage.removeItem(AUTH_STORAGE_KEY);
    setAuth(null);
  };

  if (!auth) {
    return <Login onLoginSuccess={handleLoginSuccess} />;
  }

  // Current user is the first mock user for demo purposes, with the logged-in name applied
  const users = mockUsers.map((u, i) => (i === 0 ? { ...u, name: auth.name } : u));
  const currentUser = users[0];

  return (
    <div className="app-layout" id="app-layout">
      <Sidebar users={users} currentUser={currentUser} onLogout={handleLogout} />
      <MainContent users={users} currentUser={currentUser} />
    </div>
  );
}

export default App;
