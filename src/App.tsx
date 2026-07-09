import { useState } from 'react';
import './App.css';
import { Sidebar } from './components/Sidebar';
import { MainContent } from './components/MainContent';
import { Login } from './components/Login';
import { mockUsers } from './mockData';

function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [loginName, setLoginName] = useState('');

  if (!isAuthenticated) {
    return (
      <Login
        onLoginSuccess={(name) => {
          setLoginName(name);
          setIsAuthenticated(true);
        }}
      />
    );
  }

  // Current user is the first mock user for demo purposes, with the logged-in name applied
  const users = loginName
    ? mockUsers.map((u, i) => (i === 0 ? { ...u, name: loginName } : u))
    : mockUsers;
  const currentUser = users[0];

  return (
    <div className="app-layout" id="app-layout">
      <Sidebar users={users} currentUser={currentUser} />
      <MainContent users={users} currentUser={currentUser} />
    </div>
  );
}

export default App;
