import { useCallback, useMemo } from 'react'
import { Link } from 'react-router-dom'
import AdvisorSwitch from '../components/AdvisorSwitch.tsx'
import { ErrorBanner, Loading } from '../components/Feedback.tsx'
import StatCard from '../components/StatCard.tsx'
import SuggestionTable from '../components/SuggestionTable.tsx'
import { api } from '../lib/api.ts'
import { isLowStock } from '../lib/domain.ts'
import { useApi } from '../lib/useApi.ts'

export default function DashboardPage() {
  const load = useCallback(
    () => Promise.all([api.listProducts(), api.listSuggestions({ status: 'PENDING' })]),
    [],
  )
  const { data, error, loading, reload } = useApi(load)
  const [products, pending] = data ?? [[], []]

  const productNames = useMemo(() => new Map(products.map((p) => [p.id, p.name])), [products])
  const lowStock = products.filter(isLowStock)
  const unitsOnOrder = products.reduce((sum, p) => sum + p.onOrderStock, 0)

  return (
    <section>
      <div className="row between">
        <h1>Dashboard</h1>
        <button className="secondary" onClick={reload}>
          Refresh
        </button>
      </div>
      <AdvisorSwitch />
      <ErrorBanner message={error} />
      {loading && !data ? (
        <Loading />
      ) : (
        <>
          <div className="stats">
            <StatCard label="Products" value={products.length} to="/products" />
            <StatCard label="Low stock" value={lowStock.length} warn={lowStock.length > 0} />
            <StatCard label="Units on order" value={unitsOnOrder} />
            <StatCard label="Awaiting review" value={pending.length} warn={pending.length > 0}
              to="/suggestions?status=PENDING" />
          </div>

          <h2>Awaiting review</h2>
          <SuggestionTable suggestions={pending} productNames={productNames} onReviewed={reload} />

          <h2>Low stock</h2>
          {lowStock.length === 0 ? (
            <p className="muted">Every product is above its low-stock threshold.</p>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Product</th>
                    <th className="num">On hand</th>
                    <th className="num">On order</th>
                    <th className="num">Threshold</th>
                  </tr>
                </thead>
                <tbody>
                  {lowStock.map((p) => (
                    <tr key={p.id}>
                      <td>
                        <Link to={`/products/${p.id}`}>{p.name}</Link>
                      </td>
                      <td className="num warn">{p.currentStock}</td>
                      <td className="num">{p.onOrderStock}</td>
                      <td className="num">{p.lowStockThreshold}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </section>
  )
}
