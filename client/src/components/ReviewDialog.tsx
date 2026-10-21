import { useEffect, useRef, useState, type FormEvent } from 'react'
import { createPortal } from 'react-dom'
import { api, errorMessage } from '../lib/api.ts'
import { formatMoney } from '../lib/format.ts'
import type { ReviewAction, Suggestion } from '../types.ts'
import { ErrorBanner } from './Feedback.tsx'

interface Props {
  suggestion: Suggestion
  action: ReviewAction
  onClose: () => void
  onReviewed: (updated: Suggestion) => void
}

function effectOf(suggestion: Suggestion, action: ReviewAction): string {
  if (action === 'reject') {
    return 'Nothing changes. Suggestions of this kind are paused for this product for the cooldown period, '
      + 'and your note is shown to the AI advisor next time.'
  }
  return suggestion.type === 'PRICING'
    ? `Product ${suggestion.productId}'s price changes to ${formatMoney(suggestion.newPrice)}.`
    : `${suggestion.quantity ?? 0} units are added to on-order stock until the delivery is received.`
}

/**
 * Modal confirmation for accepting or rejecting a suggestion, with an optional note. Rendered into
 * document.body so it does not inherit table-cell styles from where the buttons sit.
 */
export default function ReviewDialog({ suggestion, action, onClose, onReviewed }: Props) {
  const dialog = useRef<HTMLDialogElement>(null)
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  useEffect(() => {
    dialog.current?.showModal()
  }, [])

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(undefined)
    try {
      const trimmed = note.trim() || undefined
      const updated = action === 'accept'
        ? await api.acceptSuggestion(suggestion.id, trimmed)
        : await api.rejectSuggestion(suggestion.id, trimmed)
      onReviewed(updated)
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  const verb = action === 'accept' ? 'Accept' : 'Reject'
  return createPortal(
    <dialog ref={dialog} className="dialog" onClose={onClose} aria-labelledby="review-title">
      <form className="stack" onSubmit={submit}>
        <h2 id="review-title">
          {verb} suggestion #{suggestion.id}
        </h2>
        <p>{effectOf(suggestion, action)}</p>
        <label>
          {action === 'reject' ? 'Reason (recommended)' : 'Note (optional)'}
          <textarea rows={3} maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} autoFocus />
        </label>
        <ErrorBanner message={error} />
        <div className="row end">
          <button type="button" className="secondary" onClick={() => dialog.current?.close()}>
            Cancel
          </button>
          <button type="submit" className={action === 'reject' ? 'danger' : ''} disabled={busy}>
            {busy ? 'Saving…' : verb}
          </button>
        </div>
      </form>
    </dialog>,
    document.body,
  )
}
