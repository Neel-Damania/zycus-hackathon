import { useCallback, type ChangeEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ErrorBanner, Loading } from '../components/Feedback.tsx'
import SuggestionTable from '../components/SuggestionTable.tsx'
import { api } from '../lib/api.ts'
import { SUGGESTION_STATUSES, SUGGESTION_TYPES } from '../lib/domain.ts'
import { humanize } from '../lib/format.ts'
import { useApi } from '../lib/useApi.ts'
import type { Suggestion } from '../types.ts'

export default function SuggestionsPage() {
  const [params, setParams] = useSearchParams()
  const productId = params.get('productId') ?? ''
  const type = params.get('type') ?? ''
  const status = params.get('status') ?? ''

  const load = useCallback(() => api.listSuggestions({ productId, type, status }), [productId, type, status])
  const { data: suggestions, error, loading, setData } = useApi(load)

  const setFilter = (field: string) => (e: ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const next = new URLSearchParams(params)
    if (e.target.value) next.set(field, e.target.value)
    else next.delete(field)
    setParams(next)
  }

  const replace = (updated: Suggestion) =>
    setData((suggestions ?? []).map((s) => (s.id === updated.id ? updated : s)))

  return (
    <section>
      <h1>Suggestions</h1>
      <p className="muted">
        Reorder and pricing suggestions are never applied automatically; each waits for human review.
      </p>
      <div className="row filters">
        <label>
          Product ID
          <input type="number" min="1" value={productId} onChange={setFilter('productId')} />
        </label>
        <label>
          Type
          <select value={type} onChange={setFilter('type')}>
            <option value="">All</option>
            {SUGGESTION_TYPES.map((t) => (
              <option key={t} value={t}>
                {humanize(t)}
              </option>
            ))}
          </select>
        </label>
        <label>
          Status
          <select value={status} onChange={setFilter('status')}>
            <option value="">All</option>
            {SUGGESTION_STATUSES.map((s) => (
              <option key={s} value={s}>
                {humanize(s)}
              </option>
            ))}
          </select>
        </label>
      </div>
      <ErrorBanner message={error} />
      {loading ? <Loading /> : suggestions && <SuggestionTable suggestions={suggestions} onReviewed={replace} />}
    </section>
  )
}
