import type { Category, Product, Suggestion, SuggestionStatus, SuggestionType } from '../types.ts'
import { formatMoney } from './format.ts'

export const CATEGORIES: readonly Category[] = ['ELECTRONICS', 'CLOTHING', 'BOOKS', 'HOME_AND_KITCHEN']
export const SUGGESTION_TYPES: readonly SuggestionType[] = ['REORDER', 'PRICING']
export const SUGGESTION_STATUSES: readonly SuggestionStatus[] = ['PENDING', 'ACCEPTED', 'REJECTED']

/** Same rule as the backend's LOW_STOCK trigger: stock on hand plus on order at or below the threshold. */
export const isLowStock = (p: Product) => p.currentStock + p.onOrderStock <= p.lowStockThreshold

export const recommendation = (s: Suggestion) =>
  s.type === 'REORDER' ? `Order ${s.quantity ?? 0} units` : `Price → ${formatMoney(s.newPrice)}`
