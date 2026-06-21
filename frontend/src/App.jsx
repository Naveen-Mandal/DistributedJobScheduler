import React, { useState, useEffect } from 'react';
import { api } from './services/api';
import Login from './components/Login';
import Dashboard from './components/Dashboard';
import JobList from './components/JobList';
import JobForm from './components/JobForm';
import Executions from './components/Executions';
import DlqDashboard from './components/DlqDashboard';
import { LayoutDashboard, Calendar, Activity, AlertOctagon, LogOut, ShieldCheck } from 'lucide-react';

export default function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [activeTab, setActiveTab] = useState('dashboard');
  const [formMode, setFormMode] = useState('list'); // 'list', 'create', 'edit'
  const [selectedJob, setSelectedJob] = useState(null);
  const [username, setUsername] = useState('');

  useEffect(() => {
    const authState = api.isAuthenticated();
    setIsAuthenticated(authState);
    if (authState) {
      setUsername(localStorage.getItem('username') || 'Admin');
    }
  }, []);

  const handleLoginSuccess = () => {
    setIsAuthenticated(true);
    setUsername(localStorage.getItem('username') || 'Admin');
    setActiveTab('dashboard');
  };

  const handleLogout = () => {
    api.logout();
    setIsAuthenticated(false);
    setUsername('');
  };

  // Job form routing
  const handleEditJob = (job) => {
    setSelectedJob(job);
    setFormMode('edit');
  };

  const handleCreateJob = () => {
    setSelectedJob(null);
    setFormMode('create');
  };

  const handleFormCancel = () => {
    setFormMode('list');
    setSelectedJob(null);
  };

  const handleFormSaveSuccess = () => {
    setFormMode('list');
    setSelectedJob(null);
  };

  if (!isAuthenticated) {
    return <Login onLoginSuccess={handleLoginSuccess} />;
  }

  return (
    <div className="app-container">
      {/* Sidebar navigation */}
      <aside className="sidebar">
        <div className="logo-section">
          <ShieldCheck size={28} />
          <span>Job Scheduler</span>
        </div>

        <nav className="nav-links">
          <button
            className={`nav-item ${activeTab === 'dashboard' ? 'active' : ''}`}
            onClick={() => {
              setActiveTab('dashboard');
              setFormMode('list');
            }}
          >
            <LayoutDashboard size={18} />
            <span>Dashboard</span>
          </button>

          <button
            className={`nav-item ${activeTab === 'jobs' ? 'active' : ''}`}
            onClick={() => {
              setActiveTab('jobs');
              setFormMode('list');
            }}
          >
            <Calendar size={18} />
            <span>Job Triggers</span>
          </button>

          <button
            className={`nav-item ${activeTab === 'executions' ? 'active' : ''}`}
            onClick={() => {
              setActiveTab('executions');
              setFormMode('list');
            }}
          >
            <Activity size={18} />
            <span>Live Console</span>
          </button>

          <button
            className={`nav-item ${activeTab === 'dlq' ? 'active' : ''}`}
            onClick={() => {
              setActiveTab('dlq');
              setFormMode('list');
            }}
          >
            <AlertOctagon size={18} />
            <span>DLQ Manager</span>
          </button>
        </nav>

        {/* Footer section with user credentials */}
        <div style={{ marginTop: 'auto', display: 'flex', flexDirection: 'column', gap: '16px', borderTop: '1px solid var(--border-color)', paddingTop: '20px' }}>
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Logged in as</span>
            <span style={{ fontSize: '14px', fontWeight: '600' }}>{username}</span>
          </div>
          <button className="btn btn-secondary" onClick={handleLogout} style={{ justifyContent: 'center', gap: '6px', padding: '8px' }}>
            <LogOut size={16} />
            <span>Sign Out</span>
          </button>
        </div>
      </aside>

      {/* Main viewport panels */}
      <main className="main-content">
        {activeTab === 'dashboard' && (
          <Dashboard setActiveTab={setActiveTab} />
        )}

        {activeTab === 'jobs' && (
          <>
            {formMode === 'list' && (
              <JobList onEditJob={handleEditJob} onCreateJob={handleCreateJob} />
            )}
            {(formMode === 'create' || formMode === 'edit') && (
              <JobForm
                jobToEdit={selectedJob}
                onCancel={handleFormCancel}
                onSaveSuccess={handleFormSaveSuccess}
              />
            )}
          </>
        )}

        {activeTab === 'executions' && (
          <Executions />
        )}

        {activeTab === 'dlq' && (
          <DlqDashboard />
        )}
      </main>
    </div>
  );
}
