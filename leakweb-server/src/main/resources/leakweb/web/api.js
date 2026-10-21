// Calls to the LeakWeb JSON API. Authentication is the session cookie set on the first visit.

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

async function request(method, path) {
  const response = await fetch(path, { method, headers: { Accept: 'application/json' } });
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    if (response.status === 401) document.dispatchEvent(new CustomEvent('leakweb:unauthorized'));
    throw new ApiError(response.status, body?.error?.message ?? `HTTP ${response.status}`);
  }
  return body.data;
}

export const api = {
  status: () => request('GET', '/api/status'),
  leaks: () => request('GET', '/api/leaks'),
  leak: (signature) => request('GET', `/api/leaks/${encodeURIComponent(signature)}`),
  analyses: () => request('GET', '/api/analyses'),
  deleteAnalysis: (id) => request('DELETE', `/api/analyses/${encodeURIComponent(id)}`),
  retained: () => request('GET', '/api/retained'),
  memory: () => request('GET', '/api/memory'),
  heapDump: () => request('POST', '/api/heapdump'),
  gc: () => request('POST', '/api/gc'),
  heapDumpUrl: (id) => `/api/analyses/${encodeURIComponent(id)}/hprof`,
};
