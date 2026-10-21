import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { Badge, ErrorBanner } from '../components/Feedback.tsx'
import { api, errorMessage } from '../lib/api.ts'
import { useApi } from '../lib/useApi.ts'
import type { OrderResponse } from '../types.ts'

interface Line {
  key: string
  productId: string
  quantity: string
}

const newLine = (): Line => ({ key: crypto.randomUUID(), productId: '', quantity: '1' })

export default function OrderPage() {
  const { data: products, error: loadError, reload } = useApi(api.listProducts)
  const [lines, setLines] = useState<Line[]>(() => [newLine()])
  const [result, setResult] = useState<OrderResponse>()
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)

  const update = (key: string, patch: Partial<Line>) =>
    setLines(lines.map((l) => (l.key === key ? { ...l, ...patch } : l)))

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(undefined)
    setBusy(true)
    try {
      const items = lines.map((l) => ({ productId: Number(l.productId), quantity: Number(l.quantity) }))
      setResult(await api.placeOrder(items))
      reload()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section>
      <h1>Place order</h1>
      <p className="muted">
        Simulates a sale. Each line is processed on its own; stock changes trigger a suggestion review.
      </p>
      <ErrorBanner message={loadError ?? error} />
      <form className="stack" onSubmit={submit}>
        {lines.map((l) => (
          <div key={l.key} className="row order-line">
            <select required aria-label="Product" value={l.productId}
              onChange={(e) => update(l.key, { productId: e.target.value })}>
              <option value="">Choose a product…</option>
              {products?.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.id} · {p.name} ({p.currentStock} in stock)
                </option>
              ))}
            </select>
            <input type="number" min="1" required aria-label="Quantity" value={l.quantity}
              onChange={(e) => update(l.key, { quantity: e.target.value })} />
            <button type="button" className="secondary" disabled={lines.length === 1}
              onClick={() => setLines(lines.filter((x) => x.key !== l.key))}>
              Remove
            </button>
          </div>
        ))}
        <div className="row">
          <button type="button" className="secondary" onClick={() => setLines([...lines, newLine()])}>
            Add line
          </button>
          <button type="submit" disabled={busy}>
            {busy ? 'Ordering…' : 'Place order'}
          </button>
        </div>
      </form>

      {result && (
        <>
          <h2>Result</h2>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Product</th>
                  <th className="num">Qty</th>
                  <th className="num">Stock left</th>
                  <th>Status</th>
                  <th>Message</th>
                </tr>
              </thead>
              <tbody>
                {result.items.map((item, index) => (
                  <tr key={index}>
                    <td>
                      <Link to={`/products/${item.productId}`}>{item.productName ?? item.productId}</Link>
                    </td>
                    <td className="num">{item.quantity}</td>
                    <td className="num">{item.remainingStock ?? '—'}</td>
                    <td>
                      <Badge value={item.status} />
                    </td>
                    <td>{item.message}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  )
}
