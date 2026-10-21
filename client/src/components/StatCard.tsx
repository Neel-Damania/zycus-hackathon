import { Link } from 'react-router-dom'

interface Props {
  label: string
  value: number | string
  warn?: boolean
  to?: string
}

export default function StatCard({ label, value, warn = false, to }: Props) {
  const body = (
    <>
      <span className="stat-label">{label}</span>
      <span className="stat-value">{value}</span>
    </>
  )
  const className = `stat ${warn ? 'warn' : ''}`
  return to ? (
    <Link className={`${className} stat-link`} to={to}>
      {body}
    </Link>
  ) : (
    <div className={className}>{body}</div>
  )
}
