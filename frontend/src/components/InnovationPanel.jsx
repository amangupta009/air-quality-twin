import { useEffect, useRef, useState } from 'react'
import { fetchAlerts, fetchActions, fetchAllActions, fetchAllAlerts, simulateScenario } from '../api'

// Replay/Recent-Activity screen: time-windowed events (alert + ventilation
// actions, column-wise) for the selected room, plus the what-if scenario
// simulation. The window selector controls how far back to look, or "All"
// shows the complete activity across every room (A to Z).
export default function InnovationPanel({ room, user, canControl }) {
  const [windowH, setWindowH] = useState(1)
  const [showAll, setShowAll] = useState(false)
  const [rows, setRows] = useState([])
  const [sim, setSim] = useState(null)

  const simOcc = useRef('')
  const simVent = useRef(false)
  const simTarget = useRef('')

  const isAll = showAll
  const canSeeAll = user?.role === 'ADMIN' || user?.role === 'FACILITY_MANAGER'

  useEffect(() => {
    if (!room.roomId) return
    let cancelled = false
    async function load() {
      try {
        if (isAll) {
          const [alerts, actions] = await Promise.all([
            fetchAllAlerts().catch(() => []),
            fetchAllActions().catch(() => []),
          ])
          if (cancelled) return
          const sortedAlerts = alerts.sort((a, b) => new Date(a.triggeredAt) - new Date(b.triggeredAt))
          const grid = actions.map((a) => {
            const actedAt = new Date(a.actedAt).getTime()
            let related = null
            if (a.action !== 'ENTER') {
              for (const al of sortedAlerts) {
                if (new Date(al.triggeredAt).getTime() <= actedAt) related = al
                else break
              }
            }
            return { action: a, alert: related }
          })
          grid.sort((x, y) => new Date(y.action.actedAt) - new Date(x.action.actedAt))
          setRows(grid)
          return
        }

        const since = Date.now() - windowH * 3600 * 1000
        const [alerts, actions] = await Promise.all([
          fetchAlerts(room.roomId).catch(() => []),
          fetchActions(room.roomId).catch(() => []),
        ])
        if (cancelled) return
        const sortedAlerts = alerts.sort((a, b) => new Date(a.triggeredAt) - new Date(b.triggeredAt))
        const grid = []
        for (const a of actions) {
          const actedAt = new Date(a.actedAt).getTime()
          if (actedAt < since) continue
          let related = null
          if (a.action !== 'ENTER') {
            for (const al of sortedAlerts) {
              if (new Date(al.triggeredAt).getTime() <= actedAt) related = al
              else break
            }
          }
          const alert = related && new Date(related.triggeredAt).getTime() >= since ? related : null
          grid.push({ action: a, alert })
        }
        grid.sort((x, y) => new Date(y.action.actedAt) - new Date(x.action.actedAt))
        setRows(grid)
      } catch {
        if (!cancelled) setRows([])
      }
    }
    load()
    const timer = setInterval(load, 5000)
    return () => { cancelled = true; clearInterval(timer) }
  }, [room.roomId, windowH, isAll, showAll])

  function roomName() {
    return room.roomName ?? room.roomId
  }

  async function runSim() {
    const data = await simulateScenario(
      room.roomId,
      Number(simOcc.current) || 0,
      simVent.current,
      simTarget.current ? Number(simTarget.current) : null,
    )
    setSim(data)
  }

  return (
    <div className="activity-wrap">
      <div className="panel pred-panel">
        <div className="pred-head">📈 Prediction</div>
        {!canControl && (
          <p className="sim-result">Simulation is available to Manager/Admin only.</p>
        )}
        {canControl && (
        <div className="sim-form">
          <label><span>People</span>
            <input type="number" min="0" placeholder={room.occupants ?? 0}
              onChange={(e) => simOcc.current = e.target.value} />
          </label>
          <label><span>Ventilation</span>
            <select onChange={(e) => simVent.current = e.target.value === 'ON'}>
              <option value="OFF">OFF</option>
              <option value="ON">ON</option>
            </select>
          </label>
          <label><span>Target ppm</span>
            <input type="number" placeholder="auto" onChange={(e) => simTarget.current = e.target.value} />
          </label>
          <button onClick={runSim}>Predict</button>
        </div>
        )}
        {canControl && sim && (
          <div className="sim-result">
            <p>Current CO2: <b>{sim.currentCo2}</b> ppm · {sim.occupants} people · vent {sim.ventilationOn ? 'ON' : 'OFF'}</p>
            <p>Reaches {sim.targetPpm} ppm: <b>{sim.reachLimitType === 'never' ? 'Never under these conditions' :
              sim.reachLimitType === 'already' ? 'Already at/above limit' : `in ~${sim.minutesToLimit.toFixed(1)} min`}</b></p>
          </div>
        )}
      </div>

      <div className="panel activity-panel">
        <div className="act-head">
          <span>🕐 Recent Activity · {isAll ? 'All rooms' : roomName()}</span>
          {canSeeAll && (
            <button
              className={`all-btn${isAll ? ' active' : ''}`}
              onClick={() => setShowAll(!showAll)}
              title={isAll ? 'Show this room only' : 'Show activity of all rooms'}
            >
              {isAll ? 'Room' : 'All'}
            </button>
          )}
        </div>
        <div className="rep-controls">
          <label>Window
            <select value={windowH} onChange={(e) => setWindowH(Number(e.target.value))}>
              <option value="1">1 hr</option>
              <option value="2">2 hr</option>
              <option value="3">3 hr</option>
              <option value="4">4 hr</option>
              <option value="10">10 hr</option>
              <option value="12">12 hr</option>
              <option value="15">15 hr</option>
              <option value="24">24 hr</option>
            </select>
          </label>
        </div>

        {rows.length > 0 && (
          <div className={`af-head${isAll ? ' af-all' : ''}`}>
            {isAll && <span>Room</span>}
            <span>Recent Action</span>
            <span>Related Alert</span>
          </div>
        )}
        <div className={`af-scroll${isAll ? ' af-all' : ''}`}>
          {rows.length === 0 ? (
            <p>No activity {isAll ? 'yet' : 'in this window'}.</p>
          ) : (
            rows.map((r, i) => (
            <div className="af-row" key={i}>
              {isAll && <div className="af-cell af-room">{r.action.roomId}</div>}
              <div className="af-cell">
                {r.action.action === 'ENTER' ? (
                  <span className="ev enter">🚪 Entered Room</span>
                ) : (
                  <span className={r.action.action === 'ON' ? 'ev ventilation' : 'ev vent-off'}>
                    💨 Ventilation {r.action.action}
                  </span>
                )}
                <span className="af-meta">
                  {r.action.actor} · {new Date(r.action.actedAt).toLocaleString()}
                  {r.action.note ? ` — ${r.action.note}` : ''}
                </span>
              </div>
              <div className="af-cell">
                {r.alert ? (
                  <>
                    <span className="ev alert">
                      🔴 {r.alert.metric} {r.alert.observedValue.toFixed?.(1) ?? r.alert.observedValue} (limit {r.alert.thresholdValue})
                    </span>
                    <span className="af-meta">{new Date(r.alert.triggeredAt).toLocaleString()}</span>
                  </>
                ) : (
                  <span className="af-none">—</span>
                )}
              </div>
            </div>
            ))
          )}
        </div>
      </div>
    </div>
  )
}
