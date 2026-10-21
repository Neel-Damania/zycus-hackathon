import { useCallback, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import EvaluationResults from '../components/EvaluationResults.tsx'
import { ErrorBanner, Loading } from '../components/Feedback.tsx'
import StatCard from '../components/StatCard.tsx'
import SuggestionTable from '../components/SuggestionTable.tsx'
import { api, errorMessage } from '../lib/api.ts'
import { isLowStock } from '../lib/domain.ts'
import { formatDate, formatMoney, humanize } from '../lib/format.ts'
import { useApi } from '../lib/useApi.ts'
import type { EvaluationResult } from '../types.ts'

export default function ProductDetailPage() {
  const productId = Number(useParams().productId)
  const load = useCallback(
    () => Promise.all([api.getProduct(productId), api.listSuggestions({ productId: String(productId) })]),
    [productId],
  )
  const { data, error: loadError, reload } = useApi(load)
  const [result, setResult] = useState<EvaluationResult>()
  const [actionError, setActionError] = useState<string>()
  const [busy, setBusy] = useState(false)
  const [receiveQty, setReceiveQty] = useState('')

  async function run(action: () => Promise<void>) {
    setBusy(true)
    setActionError(undefined)
    try {
      await action()
      reload()
    } catch (err) {
      setActionError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const evaluate = () => run(async () => setResult(await api.evaluateProduct(productId)))

  const receive = (e: FormEvent) => {
    e.preventDefault()
    void run(async () => {
      await api.receiveStock(productId, Number(receiveQty))
      setReceiveQty('')
    })
  }

  if (!data) return loadError ? <ErrorBanner message={loadError} /> : <Loading />
  const [product, suggestions] = data

  return (
    <section>
      <p>
        <Link to="/products">← Products</Link>
      </p>
      <div className="row between">
        <h1>{product.name}</h1>
        <button onClick={evaluate} disabled={busy}>
          {busy ? 'Working…' : 'Evaluate now'}
        </button>
      </div>
      <ErrorBanner message={actionError} />
      <div className="stats">
        <StatCard label="Stock" value={product.currentStock} warn={isLowStock(product)} />
        <StatCard label="On order" value={product.onOrderStock} />
        <StatCard label="Low-stock threshold" value={product.lowStockThreshold} />
        <StatCard label="Safety stock" value={product.safetyStock} />
        <StatCard label="Lead time" value={`${product.leadTimeDays} d`} />
        <StatCard label="Unit price" value={formatMoney(product.unitPrice)} />
      </div>
      <dl className="details">
        <dt>ID</dt>
        <dd>{product.id}</dd>
        <dt>SKU</dt>
        <dd>{product.sku}</dd>
        <dt>Category</dt>
        <dd>{humanize(product.category)}</dd>
        <dt>Description</dt>
        <dd>{product.description || '—'}</dd>
        <dt>Created</dt>
        <dd>{formatDate(product.createdAt)}</dd>
        <dt>Last updated</dt>
        <dd>{formatDate(product.lastUpdated)}</dd>
      </dl>
      <form className="row receive" onSubmit={receive}>
        <label className="inline">
          Receive delivery
          <input type="number" min="1" required placeholder="Units" value={receiveQty}
            onChange={(e) => setReceiveQty(e.target.value)} />
        </label>
        <button type="submit" className="secondary" disabled={busy}>
          Add to stock
        </button>
        <span className="muted">Clears the same number of on-order units.</span>
      </form>
      {result && (
        <>
          <h2>Latest evaluation</h2>
          <EvaluationResults results={[result]} />
        </>
      )}
      <h2>Suggestions</h2>
      <SuggestionTable suggestions={suggestions} showProduct={false} onReviewed={reload} />
    </section>
  )
}
