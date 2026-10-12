export default function RiskBadge({ level, score }) {
  return (
    <span className={`badge badge-${level.toLowerCase()}`}>
      {level} · {score}
    </span>
  )
}
