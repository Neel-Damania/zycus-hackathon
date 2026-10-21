import { Link } from 'react-router-dom'
import { ErrorBanner, Loading } from '../components/Feedback.tsx'
import { api } from '../lib/api.ts'
import { isLowStock } from '../lib/domain.ts'
import { formatMoney, humanize } from '../lib/format.ts'
import { useApi } from '../lib/useApi.ts'

export default function ProductsPage() {
  const { data: products, error, loading } = useApi(api.listProducts)

  return (
    <section>
      <div className="row between">
        <h1>Products</h1>
        <Link className="button" to="/products/new">
          New product
        </Link>
      </div>
      <ErrorBanner message={error} />
      {loading && <Loading />}
      {products?.length === 0 && (
        <p className="muted">
          No products yet. <Link to="/products/new">Create the first one.</Link>
        </p>
      )}
      {products && products.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>SKU</th>
                <th>Name</th>
                <th>Category</th>
                <th className="num">Price</th>
                <th className="num">Stock</th>
                <th className="num">On order</th>
                <th className="num">Low-stock at</th>
                <th className="num">Lead time</th>
              </tr>
            </thead>
            <tbody>
              {products.map((p) => (
                <tr key={p.id}>
                  <td>
                    <Link to={`/products/${p.id}`}>{p.id}</Link>
                  </td>
                  <td>{p.sku}</td>
                  <td>{p.name}</td>
                  <td>{humanize(p.category)}</td>
                  <td className="num">{formatMoney(p.unitPrice)}</td>
                  <td className={`num ${isLowStock(p) ? 'warn' : ''}`}>{p.currentStock}</td>
                  <td className="num">{p.onOrderStock}</td>
                  <td className="num">{p.lowStockThreshold}</td>
                  <td className="num">{p.leadTimeDays} d</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
