export function ErrorBanner({ message }: { message?: string | null }) {
  if (!message) return null
  return (
    <div className="banner error" role="alert">
      {message}
    </div>
  )
}

export function Loading() {
  return <p className="muted">Loading…</p>
}

export function Badge({ value }: { value?: string | null }) {
  if (!value) return null
  return <span className={`badge badge-${value.toLowerCase()}`}>{value.replaceAll('_', ' ')}</span>
}
