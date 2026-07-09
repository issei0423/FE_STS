import './App.css';
import { Sidebar } from './components/Sidebar';
import { MainContent } from './components/MainContent';
import { mockUsers } from './mockData';

function App() {
  // Current user is the first mock user for demo purposes
  const currentUser = mockUsers[0];

  return (
    <div className="app-layout" id="app-layout">
      <Sidebar users={mockUsers} currentUser={currentUser} />
      <MainContent users={mockUsers} currentUser={currentUser} />
    </div>
  );
}

export default App;
