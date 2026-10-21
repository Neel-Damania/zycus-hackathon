import { Link } from 'react-router-dom'
import { recommendation } from '../lib/domain.ts'
import { formatDate, formatPercent, humanize } from '../lib/format.ts'
import type { Suggestion } from '../types.ts'
import { Badge } from './Feedback.tsx'
import ReviewButtons from './ReviewButtons.tsx'

interface Props {
  suggestions: Suggestion[]
  showProduct?: boolean
  productNames?: ReadonlyMap<number, string>
  /** When given, pending rows get Accept/Reject buttons. */
  onReviewed?: (updated: Suggestion) => void
}

export default function SuggestionTable({ suggestions, showProduct = true, productNames, onReviewed }: Props) {
  if (suggestions.length === 0) return <p className="muted">No suggestions found.</p>

  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            {showProduct && <th>Product</th>}
            <th>Type</th>
            <th>Recommendation</th>
            <th>Trigger</th>
            <th>Source</th>
            <th className="num">Confidence</th>
            <th>Status</th>
            <th>Created</th>
            {onReviewed && <th>Review</th>}
          </tr>
        </thead>
        <tbody>
          {suggestions.map((s) => (
            <tr key={s.id}>
              <td>
                <Link to={`/suggestions/${s.id}`}>#{s.id}</Link>
              </td>
              {showProduct && (
                <td>
                  <Link to={`/products/${s.productId}`}>{productNames?.get(s.productId) ?? s.productId}</Link>
                </td>
              )}
              <td>
                <Badge value={s.type} />
              </td>
              <td>{recommendation(s)}</td>
              <td>{humanize(s.alertType)}</td>
              <td>
                <Badge value={s.source} />
              </td>
              <td className="num">{formatPercent(s.confidence)}</td>
              <td>
                <Badge value={s.status} />
              </td>
              <td>{formatDate(s.createdAt)}</td>
              {onReviewed && (
                <td>
                  {s.status === 'PENDING' ? (
                    <ReviewButtons suggestion={s} onReviewed={onReviewed} compact />
                  ) : (
                    <span className="muted" title={s.reviewNote ?? undefined}>
                      {formatDate(s.reviewedAt)}
                    </span>
                  )}
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
