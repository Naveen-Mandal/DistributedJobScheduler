const BASE_URL = '/api';

const getHeaders = () => {
  const token = localStorage.getItem('token');
  const headers = {
    'Content-Type': 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return headers;
};

const handleResponse = async (response) => {
  if (response.status === 401 || response.status === 403) {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    localStorage.removeItem('role');
    window.location.reload();
    throw new Error('Unauthorized');
  }
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || 'API Request Failed');
  }
  if (response.status === 204) return null;
  return response.json();
};

export const api = {
  // Auth
  login: async (username, password) => {
    const res = await fetch(`${BASE_URL}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });
    const data = await handleResponse(res);
    if (data && data.token) {
      localStorage.setItem('token', data.token);
      localStorage.setItem('username', data.username);
      localStorage.setItem('role', data.role);
    }
    return data;
  },

  logout: () => {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    localStorage.removeItem('role');
  },

  isAuthenticated: () => {
    return !!localStorage.getItem('token');
  },

  // Jobs CRUD
  getJobs: async () => {
    const res = await fetch(`${BASE_URL}/jobs`, { headers: getHeaders() });
    return handleResponse(res);
  },

  getJob: async (id) => {
    const res = await fetch(`${BASE_URL}/jobs/${id}`, { headers: getHeaders() });
    return handleResponse(res);
  },

  createJob: async (jobData) => {
    const res = await fetch(`${BASE_URL}/jobs`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify(jobData)
    });
    return handleResponse(res);
  },

  updateJob: async (id, jobData) => {
    const res = await fetch(`${BASE_URL}/jobs/${id}`, {
      method: 'PUT',
      headers: getHeaders(),
      body: JSON.stringify(jobData)
    });
    return handleResponse(res);
  },

  deleteJob: async (id) => {
    const res = await fetch(`${BASE_URL}/jobs/${id}`, {
      method: 'DELETE',
      headers: getHeaders()
    });
    return handleResponse(res);
  },

  // Job Actions
  pauseJob: async (id) => {
    const res = await fetch(`${BASE_URL}/jobs/${id}/pause`, {
      method: 'POST',
      headers: getHeaders()
    });
    return handleResponse(res);
  },

  resumeJob: async (id) => {
    const res = await fetch(`${BASE_URL}/jobs/${id}/resume`, {
      method: 'POST',
      headers: getHeaders()
    });
    return handleResponse(res);
  },

  runJob: async (id) => {
    const res = await fetch(`${BASE_URL}/jobs/${id}/run`, {
      method: 'POST',
      headers: getHeaders()
    });
    return handleResponse(res);
  },

  requeueDlqJob: async (id) => {
    const res = await fetch(`${BASE_URL}/jobs/${id}/requeue`, {
      method: 'POST',
      headers: getHeaders()
    });
    return handleResponse(res);
  },

  // Validation
  validateCron: async (cronExpression) => {
    const res = await fetch(`${BASE_URL}/jobs/validate-cron`, {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify({ cronExpression })
    });
    return handleResponse(res);
  },

  // History & Metrics
  getRecentExecutions: async () => {
    const res = await fetch(`${BASE_URL}/executions`, { headers: getHeaders() });
    return handleResponse(res);
  },

  getJobExecutions: async (id) => {
    const res = await fetch(`${BASE_URL}/jobs/${id}/executions`, { headers: getHeaders() });
    return handleResponse(res);
  },

  getDashboardStats: async () => {
    const res = await fetch(`${BASE_URL}/dashboard/stats`, { headers: getHeaders() });
    return handleResponse(res);
  },

  getSseStreamUrl: () => {
    return `${BASE_URL}/executions/stream`;
  }
};
