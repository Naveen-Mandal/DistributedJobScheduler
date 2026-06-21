import React, { useEffect, useState } from 'react';
import { api } from '../services/api';
import { AlertOctagon, RotateCw, RefreshCw, AlertCircle, Play } from 'lucide-react';

export default function DlqDashboard() {
  const [dlqJobs, setDlqJobs] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchDlqJobs = async () => {
    setLoading(true);
    try {
      const jobs = await api.getJobs();
      const executions = await api.getRecentExecutions();
      
      // Group executions by jobId and find the latest execution for each job
      const latestExecutionMap = {};
      executions.forEach((exec) => {
        const jobId = exec.jobId;
        if (!latestExecutionMap[jobId] || new Date(exec.startedAt) > new Date(latestExecutionMap[jobId].startedAt)) {
          latestExecutionMap[jobId] = exec;
        }
      });

      // Filter jobs that are failed and have reached max retries
      const deadJobsList = jobs.filter((job) => {
        const latestExec = latestExecutionMap[job.id];
        return (
          latestExec &&
          latestExec.status === 'FAILED' &&
          latestExec.retryCount >= job.maxRetries
        );
      }).map((job) => ({
        ...job,
        latestExecution: latestExecutionMap[job.id]
      }));

      setDlqJobs(deadJobsList);
    } catch (e) {
      console.error('Failed to parse DLQ metrics', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDlqJobs();
  }, []);

  const handleRequeue = async (id) => {
    try {
      await api.requeueDlqJob(id);
      alert('Task successfully requeued! Pushed back to active execution queue.');
      fetchDlqJobs();
    } catch (e) {
      alert('Requeue failed: ' + e.message);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      <div className="page-header">
        <div>
          <h2 className="page-title" style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <AlertOctagon size={28} color="var(--danger)" />
            <span>Dead Letter Queue (DLQ) Manager</span>
          </h2>
          <p className="page-subtitle">Inspect tasks that exhausted all retry policies and require operational intervention</p>
        </div>
        <button className="btn btn-secondary" onClick={fetchDlqJobs}>
          <RefreshCw size={16} />
          <span>Refresh DLQ</span>
        </button>
      </div>

      <div className="glass-card">
        {loading ? (
          <p style={{ textAlign: 'center', color: 'var(--text-muted)', padding: '20px' }}>Analyzing cluster health...</p>
        ) : dlqJobs.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '40px 20px', color: 'var(--text-muted)' }}>
            <AlertCircle size={48} color="var(--success)" style={{ margin: '0 auto 16px auto', opacity: 0.8 }} />
            <h3 style={{ color: 'var(--text-main)', marginBottom: '8px', fontSize: '18px' }}>Clean Slate</h3>
            <p>No job triggers are currently stuck in the Dead Letter Queue.</p>
          </div>
        ) : (
          <div className="table-container">
            <table>
              <thead>
                <tr>
                  <th>Dead Job Trigger</th>
                  <th>Job Type</th>
                  <th>Failed Node ID</th>
                  <th>Max Retries</th>
                  <th>Diagnostic Error Message</th>
                  <th>Operational Action</th>
                </tr>
              </thead>
              <tbody>
                {dlqJobs.map((job) => (
                  <tr key={job.id}>
                    <td>
                      <strong style={{ display: 'block', color: 'var(--danger)', fontSize: '15px' }}>{job.name}</strong>
                      <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>ID: {job.id}</span>
                    </td>
                    <td>
                      <span className="badge badge-danger">{job.jobType}</span>
                    </td>
                    <td>
                      <code style={{ background: 'rgba(239, 68, 68, 0.1)', color: 'var(--danger)' }}>
                        {job.latestExecution?.nodeId || 'unknown-host'}
                      </code>
                    </td>
                    <td>
                      <strong style={{ color: 'var(--danger)' }}>{job.latestExecution?.retryCount}/{job.maxRetries}</strong>
                    </td>
                    <td style={{ maxWidth: '300px' }}>
                      <pre style={{ 
                        margin: 0, 
                        fontFamily: 'monospace', 
                        fontSize: '11px', 
                        color: '#fca5a5', 
                        whiteSpace: 'pre-wrap', 
                        maxHeight: '80px', 
                        overflowY: 'auto',
                        background: 'rgba(0, 0, 0, 0.3)',
                        padding: '6px',
                        borderRadius: '4px',
                        border: '1px solid rgba(239, 68, 68, 0.1)'
                      }}>
                        {job.latestExecution?.errorMessage || 'No error details recorded.'}
                      </pre>
                    </td>
                    <td>
                      <button 
                        className="btn btn-primary" 
                        onClick={() => handleRequeue(job.id)}
                        style={{ background: 'var(--success)', boxShadow: '0 4px 12px var(--success-glow)' }}
                      >
                        <RotateCw size={14} />
                        <span>Requeue Task</span>
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Troubleshooting guide for operators */}
      <div className="glass-card" style={{ borderLeft: '4px solid var(--warning)' }}>
        <h4 style={{ fontSize: '15px', fontWeight: '700', marginBottom: '8px', color: 'var(--warning)' }}>DLQ Operations Guidelines</h4>
        <ul style={{ paddingLeft: '20px', fontSize: '13px', color: 'var(--text-muted)', display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <li><strong>Identify root causes:</strong> Check the diagnostic error console to see if the failure is due to dns failures, socket timeouts, or illegal shell arguments.</li>
          <li><strong>Requeue behavior:</strong> Requeuing resets the task retry counter to 0 and dispatches a fresh run event directly into the Kafka <code>job.queue</code> topic.</li>
          <li><strong>Active schedules:</strong> Requeuing does not alter Quartz schedule patterns. The cron trigger remains active according to its original parameters.</li>
        </ul>
      </div>
    </div>
  );
}
