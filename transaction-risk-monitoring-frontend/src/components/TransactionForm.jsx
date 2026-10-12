import { useState } from 'react'
import { submitTransaction } from '../api'
import { formatAmount } from '../format'
import RiskBadge from './RiskBadge'

const EMPTY_FIELDS = { amount: '', merchant: '' }

export default function TransactionForm({ accounts, onSubmitted }) {
  const [accountId, setAccountId] = useState('')
  const [search, setSearch] = useState('')
  const [fields, setFields] = useState(EMPTY_FIELDS)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')

  const query = search.trim().toLowerCase()
  const matches = query
    ? accounts.filter((account) => `${account.accountNumber} ${account.name}`.toLowerCase().includes(query))
    : accounts

  // Keep the selection inside the filtered list
  const selectedId = matches.some((account) => account.id === Number(accountId)) ? accountId : matches[0]?.id || ''
  const selectedAccount = accounts.find((account) => account.id === Number(selectedId))

  const update = (key) => (event) => setFields({ ...fields, [key]: event.target.value })

  async function handleSubmit(event) {
    event.preventDefault()
    setSubmitting(true)
    setError('')
    setResult(null)
    try {
      const saved = await submitTransaction({
        accountId: Number(selectedId),
        amount: Number(fields.amount),
        merchant: fields.merchant.trim(),
      })
      setResult(saved)
      setFields(EMPTY_FIELDS)
      onSubmitted()
    } catch (err) {
      setError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="card">
      <h2>Submit transaction</h2>

      <form className="form" onSubmit={handleSubmit}>
        <label>
          Account
          <input type="search" placeholder={`Search ${accounts.length} accounts by name or number`}
                 value={search} onChange={(event) => setSearch(event.target.value)} />
          <select value={selectedId} onChange={(event) => setAccountId(event.target.value)} required>
            {matches.length === 0 && <option value="">No matching accounts</option>}
            {matches.map((account) => (
              <option key={account.id} value={account.id}>
                {account.accountNumber} · {account.name}
              </option>
            ))}
          </select>
          {selectedAccount && (
            <span className="hint">Spending limit: {formatAmount(selectedAccount.spendingLimit)}</span>
          )}
        </label>

        <label>
          Amount
          <input type="number" min="0.01" step="0.01" placeholder="2500.00"
                 value={fields.amount} onChange={update('amount')} required />
        </label>

        <label>
          Merchant
          <input type="text" maxLength={100} placeholder="Amazon"
                 value={fields.merchant} onChange={update('merchant')} required />
        </label>

        <button type="submit" disabled={submitting || accounts.length === 0}>
          {submitting ? 'Scoring…' : 'Submit & score'}
        </button>
      </form>

      {error && <p className="error">{error}</p>}

      {result && (
        <div className={`result ${result.risk.flagged ? 'result-flagged' : 'result-ok'}`}>
          <div className="result-header">
            <strong>{result.risk.flagged ? 'Flagged for review' : 'Approved'}</strong>
            <RiskBadge level={result.risk.riskLevel} score={result.risk.score} />
          </div>
          <p className="muted small">
            #{result.transaction.id} · {result.transaction.merchant} · {formatAmount(result.transaction.amount)}
          </p>
          {result.risk.reasons.length > 0 ? (
            <ul className="reasons">
              {result.risk.reasons.map((reason) => <li key={reason}>{reason}</li>)}
            </ul>
          ) : (
            <p className="muted small">No risk rules triggered.</p>
          )}
        </div>
      )}
    </section>
  )
}
