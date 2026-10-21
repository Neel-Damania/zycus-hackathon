import { useCallback, useEffect, useState } from 'react'
import { errorMessage } from './api.ts'

interface State<T> {
  data?: T
  error?: string
  /** The loader whose result is in this state; used to tell when a newer load is in flight. */
  source?: () => Promise<T>
}

/**
 * Runs `load` and re-runs it whenever its identity changes, so wrap it in useCallback with the
 * values it depends on. Results of superseded loads are ignored.
 */
export function useApi<T>(load: () => Promise<T>) {
  const [state, setState] = useState<State<T>>({})
  const [version, setVersion] = useState(0)

  useEffect(() => {
    let active = true
    load().then(
      (data) => {
        if (active) setState({ data, source: load })
      },
      (error: unknown) => {
        if (active) setState({ error: errorMessage(error), source: load })
      },
    )
    return () => {
      active = false
    }
  }, [load, version])

  const reload = useCallback(() => setVersion((v) => v + 1), [])
  const setData = useCallback((data: T) => setState((s) => ({ ...s, data })), [])

  return { data: state.data, error: state.error, loading: state.source !== load, reload, setData }
}
