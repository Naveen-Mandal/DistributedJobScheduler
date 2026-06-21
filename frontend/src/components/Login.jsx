import React, { useState } from 'react';
import { api } from '../services/api';
import { ShieldCheck, LogIn, Loader } from 'lucide-react';

export default function Login({ onLoginSuccess }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      await api.login(username, password);
      onLoginSuccess();
    } catch (err) {
      setError('Invalid admin credentials. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-container">
      <div className="login-card glass-card">
        <div className="logo-section" style={{ justifyContent: 'center', fontSize: '24px' }}>
          <ShieldCheck size={32} color="#6366f1" />
          <span>Distributed Scheduler</span>
        </div>
        
        <h2 className="login-title">Sign In</h2>
        
        {error && (
          <div className="badge badge-danger" style={{ display: 'block', textAlign: 'center', padding: '10px', borderRadius: '6px' }}>
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <div className="form-group">
            <label htmlFor="username">Username</label>
            <input
              id="username"
              type="text"
              required
              placeholder="e.g. admin"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              disabled={loading}
            />
          </div>

          <div className="form-group">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              required
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={loading}
            />
          </div>

          <button type="submit" className="btn btn-primary" style={{ marginTop: '16px', justifyContent: 'center' }} disabled={loading}>
            {loading ? (
              <>
                <Loader size={18} className="live-dot" style={{ animationDuration: '1s', boxShadow: 'none' }} />
                <span>Authenticating...</span>
              </>
            ) : (
              <>
                <LogIn size={18} />
                <span>Sign In</span>
              </>
            )}
          </button>
        </form>
      </div>
    </div>
  );
}
