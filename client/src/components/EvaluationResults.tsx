import { Link } from 'react-router-dom'
import { recommendation } from '../lib/domain.ts'
import { humanize } from '../lib/format.ts'
import type { EvaluationResult } from '../types.ts'
import { Badge } from './Feedback.tsx'

export default function EvaluationResults({ results }: { results: EvaluationResult[] }) {
  if (results.length === 0) return null

  return (
    <div className="stack">
      {results.map((r) => (
        <div key={r.productId} className="card">
          <div className="row between">
            <strong>
              Product <Link to={`/products/${r.productId}`}>{r.productId}</Link>
            </strong>
            <Badge value={r.outcome} />
          </div>
          <div className="meta">
            {r.alertType && <span>Trigger: {humanize(r.alertType)}</span>}
            {r.source && <span>Advisor: {humanize(r.source)}</span>}
          </div>
          {r.message && <p className="reasoning">{r.message}</p>}
          {r.created.length > 0 && (
            <ul className="created">
              {r.created.map((s) => (
                <li key={s.id}>
                  <Link to={`/suggestions/${s.id}`}>#{s.id}</Link> {recommendation(s)} — pending review
                </li>
              ))}
            </ul>
          )}
        </div>
      ))}
    </div>
  )
}
