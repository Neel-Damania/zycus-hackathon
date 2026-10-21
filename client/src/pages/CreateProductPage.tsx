import { useState, type ChangeEvent, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { ErrorBanner } from '../components/Feedback.tsx'
import { api, errorMessage } from '../lib/api.ts'
import { CATEGORIES } from '../lib/domain.ts'
import { humanize } from '../lib/format.ts'
import type { Category, ProductInput } from '../types.ts'

// Inputs hold strings; they are converted once, on submit.
interface FormState {
  sku: string
  name: string
  category: string
  unitPrice: string
  description: string
  initialStock: string
  lowStockThreshold: string
  safetyStock: string
  leadTimeDays: string
}

const EMPTY: FormState = {
  sku: '',
  name: '',
  category: 'ELECTRONICS',
  unitPrice: '',
  description: '',
  initialStock: '',
  lowStockThreshold: '',
  safetyStock: '',
  leadTimeDays: '',
}

function toInput(form: FormState): ProductInput {
  return {
    sku: form.sku,
    name: form.name,
    category: form.category as Category,
    unitPrice: Number(form.unitPrice),
    description: form.description || undefined,
    initialStock: Number(form.initialStock),
    lowStockThreshold: Number(form.lowStockThreshold),
    safetyStock: Number(form.safetyStock),
    leadTimeDays: Number(form.leadTimeDays),
  }
}

type Field = HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement

export default function CreateProductPage() {
  const [form, setForm] = useState(EMPTY)
  const [error, setError] = useState<string>()
  const [saving, setSaving] = useState(false)
  const navigate = useNavigate()

  const bind = (field: keyof FormState) => ({
    value: form[field],
    onChange: (e: ChangeEvent<Field>) => setForm({ ...form, [field]: e.target.value }),
  })

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(undefined)
    setSaving(true)
    try {
      const created = await api.createProduct(toInput(form))
      navigate(`/products/${created.id}`)
    } catch (err) {
      setError(errorMessage(err))
      setSaving(false)
    }
  }

  return (
    <section>
      <h1>New product</h1>
      <ErrorBanner message={error} />
      <form className="form" onSubmit={submit}>
        <fieldset>
          <legend>Product</legend>
          <label>
            SKU
            <input required maxLength={64} {...bind('sku')} />
          </label>
          <label>
            Name
            <input required maxLength={255} {...bind('name')} />
          </label>
          <label>
            Category
            <select {...bind('category')}>
              {CATEGORIES.map((c) => (
                <option key={c} value={c}>
                  {humanize(c)}
                </option>
              ))}
            </select>
          </label>
          <label>
            Unit price
            <input type="number" min="0.01" step="0.01" required {...bind('unitPrice')} />
          </label>
          <label className="wide">
            Description
            <textarea rows={2} maxLength={255} {...bind('description')} />
          </label>
        </fieldset>
        <fieldset>
          <legend>Inventory</legend>
          <label>
            Initial stock
            <input type="number" min="0" required {...bind('initialStock')} />
          </label>
          <label>
            Low-stock threshold
            <input type="number" min="0" required {...bind('lowStockThreshold')} />
          </label>
          <label>
            Safety stock
            <input type="number" min="0" required {...bind('safetyStock')} />
          </label>
          <label>
            Supplier lead time (days)
            <input type="number" min="0" required {...bind('leadTimeDays')} />
          </label>
        </fieldset>
        <div className="row">
          <button type="submit" disabled={saving}>
            {saving ? 'Creating…' : 'Create product'}
          </button>
        </div>
      </form>
    </section>
  )
}
