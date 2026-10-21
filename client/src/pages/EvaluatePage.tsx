import { useState, type FormEvent } from 'react'
import EvaluationResults from '../components/EvaluationResults.tsx'
import { ErrorBanner } from '../components/Feedback.tsx'
import { api, errorMessage } from '../lib/api.ts'
import { useApi } from '../lib/useApi.ts'
import type { EvaluationResult } from '../types.ts'

export default function EvaluatePage() {
  const { data: products = [], error: loadError } = useApi(api.listProducts)
  const [singleId, setSingleId] = useState('')
  const [selected, setSelected] = useState<ReadonlySet<number>>(new Set())
  const [results, setResults] = useState<EvaluationResult[]>()
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)

  async function run(evaluate: () => Promise<EvaluationResult[]>) {
    setError(undefined)
    setBusy(true)
    try {
      setResults(await evaluate())
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  const evaluateOne = (e: FormEvent) => {
    e.preventDefault()
    void run(async () => [await api.evaluateProduct(Number(singleId))])
  }

  const toggle = (id: number) => {
    const next = new Set(selected)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    setSelected(next)
  }
  const allSelected = products.length > 0 && selected.size === products.length

  return (
    <section>
      <h1>Evaluate products</h1>
      <p className="muted">
        Runs the stock and demand checks now. A suggestion is created only when stock is low or demand has
        spiked, and only if one of the same kind is not already pending review or recently rejected. Every
        product is also evaluated automatically on a schedule.
      </p>
      <ErrorBanner message={loadError ?? error} />

      <div className="grid-2">
        <div className="card">
          <h2>Single product</h2>
          <form className="row" onSubmit={evaluateOne}>
            <input type="number" min="1" required placeholder="Product ID" aria-label="Product ID"
              value={singleId} onChange={(e) => setSingleId(e.target.value)} />
            <button type="submit" disabled={busy}>
              Evaluate
            </button>
          </form>
        </div>

        <div className="card">
          <div className="row between">
            <h2>Several products</h2>
            <label className="inline">
              <input type="checkbox" checked={allSelected}
                onChange={() => setSelected(allSelected ? new Set() : new Set(products.map((p) => p.id)))} />
              Select all
            </label>
          </div>
          {products.length === 0 && <p className="muted">No products yet.</p>}
          <div className="checklist">
            {products.map((p) => (
              <label key={p.id} className="inline">
                <input type="checkbox" checked={selected.has(p.id)} onChange={() => toggle(p.id)} />
                {p.id} · {p.name}
              </label>
            ))}
          </div>
          <button disabled={busy || selected.size === 0} onClick={() => run(() => api.evaluateProducts([...selected]))}>
            {busy ? 'Evaluating…' : `Evaluate ${selected.size || ''} selected`}
          </button>
        </div>
      </div>

      {results && (
        <>
          <h2>Results</h2>
          <EvaluationResults results={results} />
        </>
      )}
    </section>
  )
}
