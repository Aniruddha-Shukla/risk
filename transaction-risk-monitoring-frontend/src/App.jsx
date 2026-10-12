import { useCallback, useEffect, useState } from 'react'
import { API_URL, getAccounts, getFlaggedTransactions } from './api'
import FlaggedTransactions from './components/FlaggedTransactions'
import TransactionForm from './components/TransactionForm'

export default function App() {
  const [accounts, setAccounts] = useState([])
  const [flagged, setFlagged] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const loadDashboard = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [accountList, flaggedList] = await Promise.all([getAccounts(), getFlaggedTransactions()])
      setAccounts(accountList)
      setFlagged(flaggedList)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadDashboard()
  }, [loadDashboard])

  return (
    <div className="app">
      <header className="header">
        <div>
          <h1>Transaction Risk Monitor</h1>
          <p className="muted">Rule-based scoring for spending limits, unusual amounts and transaction velocity</p>
        </div>
        <code className="api-url" title="Backend API">{API_URL}</code>
      </header>

      {error && (
        <div className="banner">
          {error} <button className="link" onClick={loadDashboard}>Retry</button>
        </div>
      )}

      {loading && accounts.length === 0 && !error && (
        <p className="muted">Connecting to the API… a sleeping free-tier backend can take up to a minute to wake up.</p>
      )}

      <main className="grid">
        <TransactionForm accounts={accounts} onSubmitted={loadDashboard} />
        <FlaggedTransactions transactions={flagged} loading={loading} onRefresh={loadDashboard} />
      </main>
    </div>
  )
}
