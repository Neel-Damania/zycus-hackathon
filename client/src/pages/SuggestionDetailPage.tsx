import { useCallback } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Badge, ErrorBanner, Loading } from '../components/Feedback.tsx'
import ReviewButtons from '../components/ReviewButtons.tsx'
import { api } from '../lib/api.ts'
import { recommendation } from '../lib/domain.ts'
import { formatDate, formatPercent, humanize } from '../lib/format.ts'
import { useApi } from '../lib/useApi.ts'

export default function SuggestionDetailPage() {
  const suggestionId = Number(useParams().suggestionId)
  const load = useCallback(() => api.getSuggestion(suggestionId), [suggestionId])
  const { data: s, error, setData } = useApi(load)

  if (!s) return error ? <ErrorBanner message={error} /> : <Loading />

  return (
    <section>
      <p>
        <Link to="/suggestions">← Suggestions</Link>
      </p>
      <div className="row between">
        <h1>Suggestion #{s.id}</h1>
        <div className="row">
          <Badge value={s.type} />
          <Badge value={s.status} />
        </div>
      </div>
      <div className="card">
        <p className="headline">{recommendation(s)}</p>
        <p className="reasoning">{s.reasoning}</p>
        {s.status === 'PENDING' && (
          <div className="review">
            <ReviewButtons suggestion={s} onReviewed={setData} />
          </div>
        )}
      </div>
      <dl className="details">
        <dt>Product</dt>
        <dd>
          <Link to={`/products/${s.productId}`}>{s.productId}</Link>
        </dd>
        <dt>Trigger</dt>
        <dd>{humanize(s.alertType)}</dd>
        <dt>Advisor</dt>
        <dd>
          <Badge value={s.source} />
        </dd>
        <dt>Confidence</dt>
        <dd>{formatPercent(s.confidence)}</dd>
        <dt>Created</dt>
        <dd>{formatDate(s.createdAt)}</dd>
        <dt>Reviewed</dt>
        <dd>{formatDate(s.reviewedAt)}</dd>
        <dt>Review note</dt>
        <dd>{s.reviewNote || '—'}</dd>
      </dl>
    </section>
  )
}
