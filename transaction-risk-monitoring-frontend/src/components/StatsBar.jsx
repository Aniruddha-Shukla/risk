import { formatAmount } from '../format'

const countFormat = new Intl.NumberFormat()

export default function StatsBar({ stats }) {
  const flagRate = stats.transactions ? (stats.flagged / stats.transactions) * 100 : 0

  const tiles = [
    { label: 'Accounts monitored', value: countFormat.format(stats.accounts) },
    { label: 'Transactions', value: countFormat.format(stats.transactions) },
    { label: 'Flagged', value: countFormat.format(stats.flagged), detail: `${flagRate.toFixed(1)}% of transactions` },
    { label: 'Flagged volume', value: formatAmount(stats.flaggedAmount) },
  ]

  return (
    <section className="stats">
      {tiles.map((tile) => (
        <div className="stat" key={tile.label}>
          <span className="stat-label">{tile.label}</span>
          <span className="stat-value">{tile.value}</span>
          {tile.detail && <span className="muted small">{tile.detail}</span>}
        </div>
      ))}
    </section>
  )
}
