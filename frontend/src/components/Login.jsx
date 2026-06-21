import React, { useState } from 'react';
import { api } from '../services/api';
import { ShieldCheck, LogIn, Loader, UserPlus } from 'lucide-react';

export default function Login({ onLoginSuccess }) {
  const [isSignUp, setIsSignUp] = useState(false);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);

  const handleToggleMode = () => {
    setIsSignUp(!isSignUp);
    setError('');
    setSuccess('');
    setUsername('');
    setPassword('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setLoading(true);

    try {
      if (isSignUp) {
        // Register flow
        await api.register(username, password);
        setSuccess('Admin account created successfully! Please sign in.');
        setIsSignUp(false);
        setPassword('');
      } else {
        // Sign in flow
        await api.login(username, password);
        onLoginSuccess();
      }
    } catch (err) {
      if (isSignUp) {
        setError(err.message || 'Registration failed. Try a different username.');
      } else {
        setError('Invalid admin credentials. Please try again.');
      }
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
        
        <h2 className="login-title">{isSignUp ? 'Create Admin Account' : 'Sign In'}</h2>
        
        {error && (
          <div className="badge badge-danger" style={{ display: 'block', textAlign: 'center', padding: '10px', borderRadius: '6px', width: '100%' }}>
            {error}
          </div>
        )}

        {success && (
          <div className="badge badge-success" style={{ display: 'block', textAlign: 'center', padding: '10px', borderRadius: '6px', width: '100%' }}>
            {success}
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
                <span>Processing...</span>
              </>
            ) : isSignUp ? (
              <>
                <UserPlus size={18} />
                <span>Register Account</span>
              </>
            ) : (
              <>
                <LogIn size={18} />
                <span>Sign In</span>
              </>
            )}
          </button>
        </form>

        <div style={{ textAlign: 'center', marginTop: '16px' }}>
          <button 
            onClick={handleToggleMode} 
            style={{ background: 'transparent', border: 'none', color: 'var(--primary)', cursor: 'pointer', fontSize: '13px', fontWeight: '500' }}
          >
            {isSignUp 
              ? 'Already have an admin account? Sign In' 
              : "Don't have an admin account? Register / Sign Up"}
          </button>
        </div>
      </div>
    </div>
  );
}
