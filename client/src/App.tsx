import { NavLink, Route, Routes } from 'react-router-dom'
import CreateProductPage from './pages/CreateProductPage.tsx'
import DashboardPage from './pages/DashboardPage.tsx'
import EvaluatePage from './pages/EvaluatePage.tsx'
import OrderPage from './pages/OrderPage.tsx'
import ProductDetailPage from './pages/ProductDetailPage.tsx'
import ProductsPage from './pages/ProductsPage.tsx'
import SuggestionDetailPage from './pages/SuggestionDetailPage.tsx'
import SuggestionsPage from './pages/SuggestionsPage.tsx'

const links = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/products', label: 'Products', end: false },
  { to: '/orders/new', label: 'Place order', end: false },
  { to: '/suggestions', label: 'Suggestions', end: false },
  { to: '/evaluate', label: 'Evaluate', end: false },
]

export default function App() {
  return (
    <div className="shell">
      <header className="topbar">
        <span className="brand">StockPulse</span>
        <nav aria-label="Main">
          {links.map((l) => (
            <NavLink key={l.to} to={l.to} end={l.end} className={({ isActive }) => (isActive ? 'active' : '')}>
              {l.label}
            </NavLink>
          ))}
        </nav>
      </header>
      <main className="content">
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/products" element={<ProductsPage />} />
          <Route path="/products/new" element={<CreateProductPage />} />
          <Route path="/products/:productId" element={<ProductDetailPage />} />
          <Route path="/orders/new" element={<OrderPage />} />
          <Route path="/suggestions" element={<SuggestionsPage />} />
          <Route path="/suggestions/:suggestionId" element={<SuggestionDetailPage />} />
          <Route path="/evaluate" element={<EvaluatePage />} />
          <Route path="*" element={<p className="muted">Page not found.</p>} />
        </Routes>
      </main>
    </div>
  )
}
