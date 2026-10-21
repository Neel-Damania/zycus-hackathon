// Mirrors the backend DTOs (hackathon/src/main/java/.../*Response.java and *Request.java).

export type Category = 'ELECTRONICS' | 'CLOTHING' | 'BOOKS' | 'HOME_AND_KITCHEN'
export type SuggestionType = 'REORDER' | 'PRICING'
export type SuggestionStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED'
export type AlertType = 'LOW_STOCK' | 'DEMAND_SPIKE'
export type AdviceSource = 'AI' | 'RULES' | 'RULES_FALLBACK'
export type AdvisorStrategy = 'AI' | 'RULES'
export type OrderLineStatus = 'SUCCESS' | 'FAILED'
export type EvaluationOutcome =
  | 'SUGGESTIONS_CREATED'
  | 'NO_ALERT'
  | 'NO_CHANGE'
  | 'ALREADY_PENDING'
  | 'RECENTLY_REJECTED'
  | 'IN_PROGRESS'
  | 'PRODUCT_NOT_FOUND'
  | 'FAILED'

export interface Product {
  id: number
  sku: string
  name: string
  category: Category
  unitPrice: number
  description: string | null
  currentStock: number
  onOrderStock: number
  lowStockThreshold: number
  safetyStock: number
  leadTimeDays: number
  createdAt: string
  lastUpdated: string
}

export interface ProductInput {
  sku: string
  name: string
  category: Category
  unitPrice: number
  description?: string
  initialStock: number
  lowStockThreshold: number
  safetyStock: number
  leadTimeDays: number
}

export interface OrderItem {
  productId: number
  quantity: number
}

export interface OrderLineResult {
  productId: number
  productName: string | null
  quantity: number
  remainingStock: number | null
  status: OrderLineStatus
  message: string
}

export interface OrderResponse {
  items: OrderLineResult[]
}

export interface Suggestion {
  id: number
  productId: number
  type: SuggestionType
  quantity: number | null
  newPrice: number | null
  reasoning: string
  confidence: number
  alertType: AlertType | null
  source: AdviceSource | null
  status: SuggestionStatus
  createdAt: string
  reviewedAt: string | null
  reviewNote: string | null
}

export interface EvaluationResult {
  productId: number
  outcome: EvaluationOutcome
  alertType: AlertType | null
  source: AdviceSource | null
  created: Suggestion[]
  message: string | null
}

export interface AdvisorStatus {
  strategy: AdvisorStrategy
  available: AdvisorStrategy[]
}

export interface SuggestionFilters {
  productId?: string
  type?: string
  status?: string
}

export type ReviewAction = 'accept' | 'reject'
