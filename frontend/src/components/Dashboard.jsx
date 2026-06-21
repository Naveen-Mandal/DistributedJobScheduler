import React, { useEffect, useState } from 'react';
import { api } from '../services/api';
import { Calendar, Play, CheckCircle, AlertTriangle, Activity, Database } from 'lucide-react';

export default function Dashboard({ setActiveTab }) {
  const [stats, setStats] = useState({
    totalJobs: 0,
    activeJobs: 0,
    pausedJobs: 0,
    todayExecutions: 0,
    successRate: 100,
    dlqJobs: 0,
  });
  const [recentExecs, setRecentExecs] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchStatsAndRecent = async () => {
    try {
      const statsData = await api.getDashboardStats();
      const execsData = await api.getRecentExecutions();
      setStats(statsData);
      setRecentExecs(execsData.slice(0, 8)); // Only show last 8 runs
    } catch (e) {
      console.error('Failed to load dashboard data', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStatsAndRecent();
    const interval = setInterval(fetchStatsAndRecent, 8000);
    return () => clearInterval(interval);
  }, []);

  if (loading) {
    return <div className="page-header"><h2 className="page-title">Loading dashboard...</h2></div>;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '32px' }}>
      <div className="page-header">
        <div>
          <h2 className="page-title">Cluster Overview</h2>
          <p className="page-subtitle">Real-time status of the distributed scheduling cluster</p>
        </div>
        <div className="live-indicator">
          <span className="live-dot"></span>
          <span>Live Sync Active</span>
        </div>
      </div>

      {/* Stats cards grid */}
      <div className="stats-grid">
        <div className="glass-card stat-card">
          <div className="stat-header">
            <span>Total Configured Jobs</span>
            <Database size={20} color="#6366f1" />
          </div>
          <div className="stat-value">{stats.totalJobs}</div>
          <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
            <span style={{ color: 'var(--success)', fontWeight: '600' }}>{stats.activeJobs} Active</span> | {stats.pausedJobs} Paused
          </div>
        </div>

        <div className="glass-card stat-card">
          <div className="stat-header">
            <span>Executions Today</span>
            <Play size={20} color="#10b981" />
          </div>
          <div className="stat-value">{stats.todayExecutions}</div>
          <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
            High-throughput event queue
          </div>
        </div>

        <div className="glass-card stat-card">
          <div className="stat-header">
            <span>Cluster Success Rate</span>
            <CheckCircle size={20} color={stats.successRate > 90 ? "#10b981" : "#f59e0b"} />
          </div>
          <div className="stat-value" style={{ color: stats.successRate > 90 ? 'var(--success)' : 'var(--warning)' }}>
            {stats.successRate}%
          </div>
          <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
            Auto-retry handles transient errors
          </div>
        </div>

        <div 
          className="glass-card stat-card" 
          onClick={() => setActiveTab('dlq')} 
          style={{ cursor: 'pointer', border: stats.dlqJobs > 0 ? '1px solid rgba(239, 68, 68, 0.4)' : '1px solid var(--border-color)' }}
        >
          <div className="stat-header">
            <span>Dead Letter Queue (DLQ)</span>
            <AlertTriangle size={20} color={stats.dlqJobs > 0 ? "#ef4444" : "#9ca3af"} />
          </div>
          <div className="stat-value" style={{ color: stats.dlqJobs > 0 ? 'var(--danger)' : 'var(--text-main)' }}>
            {stats.dlqJobs}
          </div>
          <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
            {stats.dlqJobs > 0 ? '⚠️ Action Required' : 'All jobs processed successfully'}
          </div>
        </div>
      </div>

      {/* Recent runs summary */}
      <div className="glass-card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
          <h3 style={{ fontSize: '18px', fontWeight: '700' }}>Recent Executions</h3>
          <button className="btn btn-secondary" onClick={() => setActiveTab('executions')}>View All Logs</button>
        </div>
        
        <div className="table-container">
          {recentExecs.length === 0 ? (
            <p style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '20px' }}>No executions recorded yet.</p>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>Job ID</th>
                  <th>Status</th>
                  <th>Node ID</th>
                  <th>Trigger Time</th>
                  <th>Retry Index</th>
                </tr>
              </thead>
              <tbody>
                {recentExecs.map((exec) => (
                  <tr key={exec.id}>
                    <td>
                      <code style={{ fontSize: '11px' }}>{exec.jobId}</code>
                    </td>
                    <td>
                      <span className={`badge badge-${exec.status.toLowerCase()}`}>
                        {exec.status}
                      </span>
                    </td>
                    <td>
                      <code style={{ background: 'rgba(99,102,241,0.1)' }}>{exec.nodeId || 'N/A'}</code>
                    </td>
                    <td>
                      {new Date(exec.startedAt).toLocaleTimeString()}
                    </td>
                    <td>
                      {exec.retryCount > 0 ? (
                        <span className="badge badge-warning" style={{ fontSize: '10px', padding: '2px 6px' }}>
                          Retry #{exec.retryCount}
                        </span>
                      ) : (
                        <span style={{ color: 'var(--text-muted)', fontSize: '12px' }}>First Attempt</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
}
