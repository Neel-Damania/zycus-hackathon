import { useState } from 'react'
import { api, errorMessage } from '../lib/api.ts'
import { useApi } from '../lib/useApi.ts'
import type { AdvisorStrategy } from '../types.ts'
import { ErrorBanner } from './Feedback.tsx'

const DESCRIPTIONS: Record<AdvisorStrategy, string> = {
  AI: 'Gemini proposes; answers are checked against policy and the rules step in if it fails.',
  RULES: 'Deterministic rules only; no LLM call.',
}

const LABELS: Record<AdvisorStrategy, string> = { AI: 'AI', RULES: 'Rules' }

/** Shows the active advisor strategy and switches it for future evaluations. */
export default function AdvisorSwitch() {
  const { data: status, error: loadError, setData } = useApi(api.getAdvisor)
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)

  async function choose(strategy: AdvisorStrategy) {
    setBusy(true)
    setError(undefined)
    try {
      setData(await api.setAdvisor(strategy))
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  if (!status) return <ErrorBanner message={loadError} />

  return (
    <div className="card advisor">
      <div className="row">
        <strong>Advisor</strong>
        <div className="segmented" role="group" aria-label="Advisor strategy">
          {status.available.map((strategy) => (
            <button key={strategy} aria-pressed={strategy === status.strategy} disabled={busy}
              onClick={() => {
                if (strategy !== status.strategy) void choose(strategy)
              }}>
              {LABELS[strategy]}
            </button>
          ))}
        </div>
        <span className="muted">{DESCRIPTIONS[status.strategy]} Applies to the next evaluations.</span>
      </div>
      <ErrorBanner message={error} />
    </div>
  )
}
