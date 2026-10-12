export const API_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/+$/, '')

async function request(path, options = {}) {
  let response
  try {
    response = await fetch(`${API_URL}${path}`, options)
  } catch {
    throw new Error(`Cannot reach the API at ${API_URL}. Is the backend running?`)
  }

  const body = await response.json().catch(() => null)
  if (!response.ok) {
    throw new Error(body?.error || `Request failed with status ${response.status}`)
  }
  return body
}

export const getAccounts = () => request('/api/accounts')

export const getFlaggedTransactions = () => request('/api/transactions/flagged')

export const getStats = () => request('/api/stats')

export const submitTransaction = (transaction) =>
  request('/api/transactions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(transaction),
  })
