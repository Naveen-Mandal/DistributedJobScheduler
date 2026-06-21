import React, { useEffect, useState } from 'react';
import { api } from '../services/api';
import { Play, Pause, Trash2, Edit2, Plus, Search, HelpCircle } from 'lucide-react';

export default function JobList({ onEditJob, onCreateJob }) {
  const [jobs, setJobs] = useState([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);

  const fetchJobs = async () => {
    try {
      const data = await api.getJobs();
      setJobs(data);
    } catch (e) {
      console.error('Failed to fetch jobs list', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchJobs();
  }, []);

  const handlePause = async (id) => {
    try {
      await api.pauseJob(id);
      fetchJobs();
    } catch (e) {
      alert('Failed to pause job: ' + e.message);
    }
  };

  const handleResume = async (id) => {
    try {
      await api.resumeJob(id);
      fetchJobs();
    } catch (e) {
      alert('Failed to resume job: ' + e.message);
    }
  };

  const handleRunNow = async (id) => {
    try {
      await api.runJob(id);
      alert('Execution dispatched directly to worker queue!');
    } catch (e) {
      alert('Failed to run job: ' + e.message);
    }
  };

  const handleDelete = async (id) => {
    if (window.confirm('Are you sure you want to delete this job trigger?')) {
      try {
        await api.deleteJob(id);
        fetchJobs();
      } catch (e) {
        alert('Failed to delete job: ' + e.message);
      }
    }
  };

  const filteredJobs = jobs.filter(
    (j) =>
      j.name.toLowerCase().includes(search.toLowerCase()) ||
      j.cronExpression.includes(search) ||
      j.jobType.toLowerCase().includes(search.toLowerCase())
  );

  const renderPayloadSummary = (job) => {
    try {
      const payloadObj = JSON.parse(job.payload);
      if (job.jobType === 'HTTP_CALL') {
        return `${payloadObj.method || 'GET'} ${payloadObj.url || ''}`;
      } else if (job.jobType === 'SHELL') {
        return `$ ${payloadObj.command || ''}`;
      } else if (job.jobType === 'EMAIL') {
        return `Mail to: ${payloadObj.to || ''}`;
      }
    } catch (e) {
      return job.payload;
    }
    return '';
  };

  if (loading) {
    return <div className="page-header"><h2 className="page-title">Loading jobs...</h2></div>;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      <div className="page-header">
        <div>
          <h2 className="page-title">Configured Tasks</h2>
          <p className="page-subtitle">Manage recurring schedules and payload configurations</p>
        </div>
        <button className="btn btn-primary" onClick={onCreateJob}>
          <Plus size={16} />
          <span>New Job Trigger</span>
        </button>
      </div>

      {/* Search Bar */}
      <div className="glass-card" style={{ padding: '16px', display: 'flex', gap: '12px', alignItems: 'center' }}>
        <Search size={18} color="var(--text-muted)" />
        <input
          type="text"
          placeholder="Filter jobs by name, type, or cron pattern..."
          style={{ flex: 1, border: 'none', background: 'transparent', padding: '0' }}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      {/* Table Card */}
      <div className="glass-card">
        <div className="table-container">
          {filteredJobs.length === 0 ? (
            <p style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '20px' }}>
              No matching job triggers found.
            </p>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>Job Name</th>
                  <th>Cron Expression</th>
                  <th>Type</th>
                  <th>Payload details</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredJobs.map((job) => (
                  <tr key={job.id}>
                    <td>
                      <strong style={{ display: 'block', fontSize: '15px' }}>{job.name}</strong>
                      <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>ID: {job.id}</span>
                    </td>
                    <td>
                      <code>{job.cronExpression}</code>
                    </td>
                    <td>
                      <span className="badge badge-info" style={{ fontSize: '11px' }}>
                        {job.jobType}
                      </span>
                    </td>
                    <td style={{ maxWidth: '280px', textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
                      <span style={{ fontSize: '12px', fontFamily: 'monospace', color: 'var(--text-muted)' }}>
                        {renderPayloadSummary(job)}
                      </span>
                    </td>
                    <td>
                      <span className={`badge badge-${job.status.toLowerCase()}`}>
                        {job.status}
                      </span>
                    </td>
                    <td>
                      <div className="action-buttons">
                        <button 
                          className="icon-btn" 
                          title="Trigger Now (Ad-hoc run)"
                          onClick={() => handleRunNow(job.id)}
                        >
                          <Play size={16} color="var(--success)" />
                        </button>
                        
                        {job.status === 'ACTIVE' ? (
                          <button 
                            className="icon-btn" 
                            title="Pause Schedule"
                            onClick={() => handlePause(job.id)}
                          >
                            <Pause size={16} color="var(--warning)" />
                          </button>
                        ) : job.status === 'PAUSED' ? (
                          <button 
                            className="icon-btn" 
                            title="Resume Schedule"
                            onClick={() => handleResume(job.id)}
                          >
                            <Play size={16} color="var(--success)" />
                          </button>
                        ) : null}

                        <button 
                          className="icon-btn" 
                          title="Edit Configuration"
                          onClick={() => onEditJob(job)}
                        >
                          <Edit2 size={16} color="#6366f1" />
                        </button>

                        <button 
                          className="icon-btn icon-btn-danger" 
                          title="Remove Job"
                          onClick={() => handleDelete(job.id)}
                        >
                          <Trash2 size={16} />
                        </button>
                      </div>
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
