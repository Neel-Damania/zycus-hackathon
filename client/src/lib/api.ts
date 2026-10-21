import type {
  AdvisorStatus,
  AdvisorStrategy,
  EvaluationResult,
  OrderItem,
  OrderResponse,
  Product,
  ProductInput,
  Suggestion,
  SuggestionFilters,
} from '../types.ts'

// The browser calls the backend directly, so this origin must be in stockpulse.cors.allowed-origins.
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

/** RFC 9457 problem detail, as returned by the backend's GlobalExceptionHandler. */
interface ProblemDetail {
  title?: string
  detail?: string
  errors?: { field: string; message: string }[]
}

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(`${API_BASE_URL}/api${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json' },
  })
  const text = await res.text()
  const body: unknown = text ? JSON.parse(text) : null
  if (!res.ok) {
    throw new ApiError(res.status, problemMessage(body, res.status))
  }
  return body as T
}

function problemMessage(body: unknown, status: number): string {
  const problem = (body ?? {}) as ProblemDetail
  const fieldErrors = problem.errors?.map((e) => `${e.field}: ${e.message}`).join(', ')
  return fieldErrors || problem.detail || problem.title || `Request failed (${status})`
}

function post<T>(path: string, body?: unknown): Promise<T> {
  return request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) })
}

function query(filters: SuggestionFilters): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(filters)) {
    if (value) params.set(key, value)
  }
  const qs = params.toString()
  return qs ? `?${qs}` : ''
}

export const api = {
  listProducts: () => request<Product[]>('/products'),
  getProduct: (id: number) => request<Product>(`/products/${id}`),
  createProduct: (input: ProductInput) => post<Product>('/products', input),
  receiveStock: (id: number, quantity: number) => post<Product>(`/products/${id}/receive`, { quantity }),

  placeOrder: (items: OrderItem[]) => post<OrderResponse>('/sales/order', { items }),

  listSuggestions: (filters: SuggestionFilters = {}) => request<Suggestion[]>(`/suggestions${query(filters)}`),
  getSuggestion: (id: number) => request<Suggestion>(`/suggestions/${id}`),
  acceptSuggestion: (id: number, note?: string) => post<Suggestion>(`/suggestions/${id}/accept`, { note }),
  rejectSuggestion: (id: number, note?: string) => post<Suggestion>(`/suggestions/${id}/reject`, { note }),
  evaluateProduct: (productId: number) => post<EvaluationResult>(`/suggestions/evaluate/${productId}`),
  evaluateProducts: (productIds: number[]) =>
    post<EvaluationResult[]>('/suggestions/evaluate', { productIds }),

  getAdvisor: () => request<AdvisorStatus>('/advisor'),
  setAdvisor: (strategy: AdvisorStrategy) =>
    request<AdvisorStatus>('/advisor', { method: 'PUT', body: JSON.stringify({ strategy }) }),
}

export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error)
}
