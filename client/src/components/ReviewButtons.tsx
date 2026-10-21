import { useState } from 'react'
import type { ReviewAction, Suggestion } from '../types.ts'
import ReviewDialog from './ReviewDialog.tsx'

interface Props {
  suggestion: Suggestion
  onReviewed: (updated: Suggestion) => void
  compact?: boolean
}

export default function ReviewButtons({ suggestion, onReviewed, compact = false }: Props) {
  const [action, setAction] = useState<ReviewAction | null>(null)

  return (
    <div className={`row ${compact ? 'compact' : ''}`}>
      <button onClick={() => setAction('accept')}>Accept</button>
      <button className="secondary" onClick={() => setAction('reject')}>
        Reject
      </button>
      {action && (
        <ReviewDialog
          suggestion={suggestion}
          action={action}
          onClose={() => setAction(null)}
          onReviewed={(updated) => {
            setAction(null)
            onReviewed(updated)
          }}
        />
      )}
    </div>
  )
}
