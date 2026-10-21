export const formatMoney = (value: number | null | undefined) => (value == null ? '—' : value.toFixed(2))

export const formatDate = (value: string | null | undefined) => (value ? new Date(value).toLocaleString() : '—')

export const formatPercent = (fraction: number) => `${Math.round(fraction * 100)}%`

/** LOW_STOCK -> "low stock" */
export const humanize = (value: string | null | undefined) =>
  value ? value.toLowerCase().replaceAll('_', ' ') : '—'
