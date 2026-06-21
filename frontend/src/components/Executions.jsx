import React, { useEffect, useState } from 'react';
import { api } from '../services/api';
import { RefreshCw, Search, Monitor, AlertTriangle, Play, HelpCircle } from 'lucide-react';

export default function Executions() {
  const [executions, setExecutions] = useState([]);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [loading, setLoading] = useState(true);
  const [sseConnected, setSseConnected] = useState(false);

  const fetchHistory = async () => {
    try {
      const data = await api.getRecentExecutions();
      setExecutions(data);
    } catch (e) {
      console.error('Failed to load executions history', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // 1. Fetch initial history
    fetchHistory();

    // 2. Connect to Server-Sent Events stream for real-time logs
    const token = localStorage.getItem('token');
    const sseUrl = `${api.getSseStreamUrl()}?token=${token}`;
    const eventSource = new EventSource(sseUrl);

    eventSource.onopen = () => {
      setSseConnected(true);
      console.log('SSE Stream connected');
    };

    eventSource.onerror = (e) => {
      setSseConnected(false);
      console.warn('SSE Stream disconnected/error', e);
    };

    // Listen for custom execution update event
    eventSource.addEventListener('execution-update', (event) => {
      try {
        const updatedExec = JSON.parse(event.data);
        console.log('Real-time execution update:', updatedExec);

        setExecutions((prev) => {
          // Check if this execution log is already in the list
          const existsIndex = prev.findIndex((e) => e.id === updatedExec.id);
          if (existsIndex > -1) {
            // Update the existing status (e.g. RUNNING -> SUCCESS)
            const updatedList = [...prev];
            updatedList[existsIndex] = updatedExec;
            return updatedList;
          } else {
            // Prepend new run log
            return [updatedExec, ...prev];
          }
        });
      } catch (err) {
        console.error('Error parsing SSE event data', err);
      }
    });

    return () => {
      eventSource.close();
    };
  }, []);

  const getDuration = (exec) => {
    if (!exec.startedAt || !exec.finishedAt) return '-';
    const start = new Date(exec.startedAt).getTime();
    const end = new Date(exec.finishedAt).getTime();
    const diff = end - start;
    return `${diff} ms`;
  };

  const filteredExecutions = executions.filter((exec) => {
    const matchesSearch =
      exec.jobId.toLowerCase().includes(search.toLowerCase()) ||
      exec.id.toLowerCase().includes(search.toLowerCase()) ||
      (exec.nodeId && exec.nodeId.toLowerCase().includes(search.toLowerCase())) ||
      (exec.errorMessage && exec.errorMessage.toLowerCase().includes(search.toLowerCase()));

    const matchesStatus =
      statusFilter === 'ALL' || exec.status.toUpperCase() === statusFilter;

    return matchesSearch && matchesStatus;
  });

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      <div className="page-header">
        <div>
          <h2 className="page-title">Live Executions Console</h2>
          <p className="page-subtitle">Real-time log of events triggered across the cluster</p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <div className="live-indicator">
            <span className={sseConnected ? "live-dot" : "live-dot"} style={{ backgroundColor: sseConnected ? 'var(--success)' : 'var(--danger)', boxShadow: sseConnected ? '0 0 8px var(--success)' : 'none' }}></span>
            <span style={{ color: sseConnected ? 'var(--success)' : 'var(--danger)' }}>
              {sseConnected ? 'Real-Time Connected' : 'Reconnecting...'}
            </span>
          </div>
          <button className="btn btn-secondary" onClick={fetchHistory}>
            <RefreshCw size={16} />
            <span>Refresh</span>
          </button>
        </div>
      </div>

      {/* Filter and search bar */}
      <div className="glass-card" style={{ padding: '20px', display: 'flex', flexWrap: 'wrap', gap: '16px', alignItems: 'center' }}>
        <div style={{ flex: 1, display: 'flex', gap: '12px', alignItems: 'center', borderBottom: '1px solid var(--border-color)', paddingBottom: '6px' }}>
          <Search size={18} color="var(--text-muted)" />
          <input
            type="text"
            placeholder="Search by Job ID, Run ID, Node ID, or errors..."
            style={{ flex: 1, border: 'none', background: 'transparent', padding: '0', outline: 'none' }}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <label htmlFor="status-filter">Filter Status:</label>
          <select 
            id="status-filter" 
            value={statusFilter} 
            onChange={(e) => setStatusFilter(e.target.value)}
            style={{ padding: '8px 12px' }}
          >
            <option value="ALL">All Executions</option>
            <option value="RUNNING">Running</option>
            <option value="SUCCESS">Success</option>
            <option value="FAILED">Failed</option>
          </select>
        </div>
      </div>

      {/* Main Console logs list */}
      <div className="glass-card">
        <div className="table-container">
          {loading && executions.length === 0 ? (
            <p style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '20px' }}>Loading events...</p>
          ) : filteredExecutions.length === 0 ? (
            <p style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '20px' }}>No executions match the filter rules.</p>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>Execution ID</th>
                  <th>Job ID</th>
                  <th>Status</th>
                  <th>Node ID</th>
                  <th>Duration</th>
                  <th>Started At</th>
                </tr>
              </thead>
              <tbody>
                {filteredExecutions.map((exec) => (
                  <React.Fragment key={exec.id}>
                    <tr>
                      <td>
                        <strong style={{ display: 'block', fontSize: '13px' }}>
                          <span style={{ color: 'var(--text-muted)' }}>RUN-</span>{exec.id.substring(0, 8)}
                        </strong>
                        <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{exec.id}</span>
                      </td>
                      <td>
                        <code style={{ fontSize: '11px' }}>{exec.jobId}</code>
                      </td>
                      <td>
                        <span className={`badge badge-${exec.status.toLowerCase()}`}>
                          {exec.status}
                        </span>
                      </td>
                      <td>
                        <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                          <Monitor size={14} color="var(--primary)" />
                          <code style={{ background: 'rgba(99, 102, 241, 0.1)', color: 'var(--text-main)' }}>
                            {exec.nodeId || 'worker-host'}
                          </code>
                        </span>
                      </td>
                      <td>
                        {getDuration(exec)}
                      </td>
                      <td>
                        {new Date(exec.startedAt).toLocaleTimeString()}
                        <span style={{ display: 'block', fontSize: '11px', color: 'var(--text-muted)' }}>
                          {new Date(exec.startedAt).toLocaleDateString()}
                        </span>
                      </td>
                    </tr>
                    
                    {/* Collapsible Error Message row if execution failed */}
                    {exec.status === 'FAILED' && exec.errorMessage && (
                      <tr>
                        <td colSpan="6" style={{ background: 'rgba(239, 68, 68, 0.03)', padding: '12px 24px' }}>
                          <div style={{ display: 'flex', gap: '8px', color: 'var(--danger)' }}>
                            <AlertTriangle size={16} style={{ flexShrink: 0, marginTop: '2px' }} />
                            <div>
                              <strong style={{ fontSize: '12px', display: 'block' }}>Exception Message (Retry #{exec.retryCount}):</strong>
                              <pre style={{ margin: '4px 0 0 0', fontFamily: 'monospace', fontSize: '12px', whiteSpace: 'pre-wrap', color: '#fca5a5' }}>
                                {exec.errorMessage}
                              </pre>
                            </div>
                          </div>
                        </td>
                      </tr>
                    )}
                  </React.Fragment>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
}
