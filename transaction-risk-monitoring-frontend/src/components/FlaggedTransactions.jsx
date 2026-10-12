import { formatAmount, formatTime } from '../format'
import RiskBadge from './RiskBadge'

export default function FlaggedTransactions({ transactions, loading, onRefresh }) {
  return (
    <section className="card">
      <div className="card-header">
        <h2>
          Flagged transactions <span className="count">{transactions.length}</span>
        </h2>
        <button className="secondary" onClick={onRefresh} disabled={loading}>
          {loading ? 'Loading…' : 'Refresh'}
        </button>
      </div>

      {transactions.length === 0 ? (
        <p className="muted">{loading ? 'Loading…' : 'No flagged transactions yet.'}</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Time</th>
                <th>Account</th>
                <th>Merchant</th>
                <th className="num">Amount</th>
                <th>Risk</th>
                <th>Reasons</th>
              </tr>
            </thead>
            <tbody>
              {transactions.map((t) => (
                <tr key={t.transactionId}>
                  <td className="nowrap">{formatTime(t.transactionTime)}</td>
                  <td>
                    {t.accountName}
                    <div className="muted small">{t.accountNumber}</div>
                  </td>
                  <td>{t.merchant}</td>
                  <td className="num">{formatAmount(t.amount)}</td>
                  <td><RiskBadge level={t.riskLevel} score={t.score} /></td>
                  <td>
                    <ul className="reasons">
                      {t.reasons.map((reason) => <li key={reason}>{reason}</li>)}
                    </ul>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
