import React, { useState, useEffect } from 'react';
import { api } from '../services/api';
import { ArrowLeft, Save, Sparkles, Check, AlertCircle } from 'lucide-react';

export default function JobForm({ jobToEdit, onCancel, onSaveSuccess }) {
  const isEdit = !!jobToEdit;

  const [name, setName] = useState('');
  const [cronExpression, setCronExpression] = useState('0 */5 * * * ?');
  const [jobType, setJobType] = useState('HTTP_CALL');
  const [maxRetries, setMaxRetries] = useState(3);

  // Dynamic payload states
  const [httpUrl, setHttpUrl] = useState('');
  const [httpMethod, setHttpMethod] = useState('GET');
  
  const [shellCommand, setShellCommand] = useState('');
  
  const [emailTo, setEmailTo] = useState('');
  const [emailSubject, setEmailSubject] = useState('');
  const [emailBody, setEmailBody] = useState('');

  // Validation States
  const [cronValidation, setCronValidation] = useState({ valid: true, message: 'Default: Runs every 5 minutes.' });
  const [validating, setValidating] = useState(false);

  useEffect(() => {
    if (isEdit) {
      setName(jobToEdit.name);
      setCronExpression(jobToEdit.cronExpression);
      setJobType(jobToEdit.jobType);
      setMaxRetries(jobToEdit.maxRetries);

      try {
        const payloadObj = JSON.parse(jobToEdit.payload);
        if (jobToEdit.jobType === 'HTTP_CALL') {
          setHttpUrl(payloadObj.url || '');
          setHttpMethod(payloadObj.method || 'GET');
        } else if (jobToEdit.jobType === 'SHELL') {
          setShellCommand(payloadObj.command || '');
        } else if (jobToEdit.jobType === 'EMAIL') {
          setEmailTo(payloadObj.to || '');
          setEmailSubject(payloadObj.subject || '');
          setEmailBody(payloadObj.body || '');
        }
      } catch (e) {
        console.error('Failed to parse payload', e);
      }
    }
  }, [jobToEdit, isEdit]);

  // Debounced/Triggered Cron Validation
  useEffect(() => {
    if (!cronExpression) {
      setCronValidation({ valid: false, message: 'Cron expression cannot be empty.' });
      return;
    }
    
    const delayDebounce = setTimeout(async () => {
      setValidating(true);
      try {
        const response = await api.validateCron(cronExpression);
        setCronValidation(response);
      } catch (e) {
        setCronValidation({ valid: false, message: 'Server validation error' });
      } finally {
        setValidating(false);
      }
    }, 600); // 600ms debounce

    return () => clearTimeout(delayDebounce);
  }, [cronExpression]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!cronValidation.valid) {
      alert('Please enter a valid Quartz Cron Expression before saving.');
      return;
    }

    // Prepare payload object based on type
    let payloadObj = {};
    if (jobType === 'HTTP_CALL') {
      payloadObj = { url: httpUrl, method: httpMethod };
    } else if (jobType === 'SHELL') {
      payloadObj = { command: shellCommand };
    } else if (jobType === 'EMAIL') {
      payloadObj = { to: emailTo, subject: emailSubject, body: emailBody };
    }

    const jobData = {
      name,
      cronExpression,
      jobType,
      payload: JSON.stringify(payloadObj),
      maxRetries: parseInt(maxRetries, 10),
    };

    try {
      if (isEdit) {
        await api.updateJob(jobToEdit.id, jobData);
      } else {
        await api.createJob(jobData);
      }
      onSaveSuccess();
    } catch (err) {
      alert('Failed to save job trigger: ' + err.message);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      <div className="page-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <button className="icon-btn" onClick={onCancel} style={{ padding: '8px' }}>
            <ArrowLeft size={20} />
          </button>
          <div>
            <h2 className="page-title">{isEdit ? 'Modify Task Configuration' : 'Register New Task'}</h2>
            <p className="page-subtitle">{isEdit ? `Editing Job ID: ${jobToEdit.id}` : 'Create a new dynamic trigger definition'}</p>
          </div>
        </div>
      </div>

      <div className="glass-card" style={{ maxWidth: '800px' }}>
        <form onSubmit={handleSubmit}>
          {/* Section: General info */}
          <div className="form-group">
            <label htmlFor="job-name">Task Name</label>
            <input
              id="job-name"
              type="text"
              required
              placeholder="e.g. Sync Database Logs"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </div>

          <div className="form-group">
            <label htmlFor="cron-exp">Quartz Cron Expression</label>
            <input
              id="cron-exp"
              type="text"
              required
              placeholder="e.g. 0 */5 * * * ?"
              value={cronExpression}
              onChange={(e) => setCronExpression(e.target.value)}
            />
            {/* Cron helper feedback */}
            <div style={{ marginTop: '6px', display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px' }}>
              {validating ? (
                <span style={{ color: 'var(--text-muted)' }}>Validating structure...</span>
              ) : cronValidation.valid ? (
                <span style={{ color: 'var(--success)', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                  <Check size={14} />
                  <span>{cronValidation.message}</span>
                </span>
              ) : (
                <span style={{ color: 'var(--danger)', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                  <AlertCircle size={14} />
                  <span>{cronValidation.message}</span>
                </span>
              )}
            </div>
            <small style={{ color: 'var(--text-muted)', marginTop: '4px', fontSize: '11px', display: 'block' }}>
              Quick Guides: <code>0 * * * * ?</code> (Every Minute) | <code>0 0 12 * * ?</code> (Daily at Noon) | <code>0 0/15 * * * ?</code> (Every 15 mins)
            </small>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px' }}>
            <div className="form-group">
              <label htmlFor="job-type">Task Executor Type</label>
              <select
                id="job-type"
                value={jobType}
                onChange={(e) => setJobType(e.target.value)}
                disabled={isEdit} // Type locking on update is typical to preserve integrity
              >
                <option value="HTTP_CALL">HTTP Call (REST Webhooks)</option>
                <option value="SHELL">Shell Execution (Sandbox Commands)</option>
                <option value="EMAIL">Email Notification (Reports)</option>
              </select>
            </div>

            <div className="form-group">
              <label htmlFor="max-retries">Max Failover Retries</label>
              <input
                id="max-retries"
                type="number"
                min="0"
                max="10"
                required
                value={maxRetries}
                onChange={(e) => setMaxRetries(e.target.value)}
              />
            </div>
          </div>

          <hr style={{ border: 'none', height: '1px', background: 'var(--border-color)', margin: '24px 0' }} />

          {/* Section: Dynamic Payloads */}
          {jobType === 'HTTP_CALL' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <h3 style={{ fontSize: '16px', fontWeight: '700', color: 'var(--primary)' }}>HTTP Webhook Details</h3>
              <div style={{ display: 'grid', gridTemplateColumns: '180px 1fr', gap: '16px' }}>
                <div className="form-group">
                  <label htmlFor="http-method">Request Method</label>
                  <select id="http-method" value={httpMethod} onChange={(e) => setHttpMethod(e.target.value)}>
                    <option value="GET">GET</option>
                    <option value="POST">POST</option>
                    <option value="PUT">PUT</option>
                    <option value="DELETE">DELETE</option>
                  </select>
                </div>
                <div className="form-group">
                  <label htmlFor="http-url">Endpoint URL</label>
                  <input
                    id="http-url"
                    type="url"
                    required
                    placeholder="https://api.domain.com/v1/trigger"
                    value={httpUrl}
                    onChange={(e) => setHttpUrl(e.target.value)}
                  />
                </div>
              </div>
            </div>
          )}

          {jobType === 'SHELL' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <h3 style={{ fontSize: '16px', fontWeight: '700', color: 'var(--primary)' }}>Shell Sandbox Execution</h3>
              <div className="form-group">
                <label htmlFor="shell-cmd">Command String</label>
                <input
                  id="shell-cmd"
                  type="text"
                  required
                  placeholder="e.g. echo 'backup complete' && curl -s http://status-ping.com"
                  value={shellCommand}
                  onChange={(e) => setShellCommand(e.target.value)}
                />
                <small style={{ color: 'var(--warning)', marginTop: '4px', fontSize: '12px' }}>
                  ⚠️ Only safe whitelisted commands are permitted. Parameters containing pipe characters or shell escapes will trigger validation blocks.
                </small>
              </div>
            </div>
          )}

          {jobType === 'EMAIL' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <h3 style={{ fontSize: '16px', fontWeight: '700', color: 'var(--primary)' }}>Email Report Configurations</h3>
              <div className="form-group">
                <label htmlFor="email-to">Recipient Address</label>
                <input
                  id="email-to"
                  type="email"
                  required
                  placeholder="admin-alerts@company.com"
                  value={emailTo}
                  onChange={(e) => setEmailTo(e.target.value)}
                />
              </div>
              <div className="form-group">
                <label htmlFor="email-subject">Subject Title</label>
                <input
                  id="email-subject"
                  type="text"
                  required
                  placeholder="Distributed Task Success Report"
                  value={emailSubject}
                  onChange={(e) => setEmailSubject(e.target.value)}
                />
              </div>
              <div className="form-group">
                <label htmlFor="email-body">Message Body</label>
                <textarea
                  id="email-body"
                  rows="4"
                  required
                  placeholder="Hello Admin, the scheduled payload execution succeeded..."
                  value={emailBody}
                  onChange={(e) => setEmailBody(e.target.value)}
                />
              </div>
            </div>
          )}

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px', marginTop: '32px' }}>
            <button type="button" className="btn btn-secondary" onClick={onCancel}>Cancel</button>
            <button type="submit" className="btn btn-primary" disabled={!cronValidation.valid || validating}>
              <Save size={16} />
              <span>Save Schedule</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
