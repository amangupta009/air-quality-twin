import { useEffect, useRef, useState } from 'react'
import { fetchReplay, simulateScenario } from '../api'

// The distinguishing contribution: time-travel replay of a room's whole story
// (CO2 line + alert/ventilation events) and what-if scenario simulation.
export default function InnovationPanel({ room, user }) {
  const [minutes, setMinutes] = useState(60)
  const [replay, setReplay] = useState(null)
  const [frame, setFrame] = useState(0)
  const [playing, setPlaying] = useState(false)
  const [sim, setSim] = useState(null)

  const simOcc = useRef('')
  const simVent = useRef(false)
  const simTarget = useRef('')

  async function loadReplay(m = minutes) {
    const data = await fetchReplay(room.roomId, m)
    setReplay(data)
    setFrame(0)
  }

  useEffect(() => { if (room.roomId) loadReplay() }, [room.roomId])

  // Playback loop
  useEffect(() => {
    if (!playing || !replay) return
    const t = setInterval(() => {
      setFrame((f) => {
        if (f >= replay.samples.length - 1) { setPlaying(false); return f; }
        return f + 1;
      })
    }, 120)
    return () => clearInterval(t)
  }, [playing, replay])

  async function runSim() {
    const data = await simulateScenario(
      room.roomId,
      Number(simOcc.current) || 0,
      simVent.current,
      simTarget.current ? Number(simTarget.current) : null,
    )
    setSim(data)
  }

  const samples = replay?.samples || []
  const visible = samples.slice(0, frame + 1)
  const events = replay?.events || []

  return (
    <div className="panel innovation">
      <h3>Innovation Lab · {room.roomName ?? room.roomId}</h3>

      <div className="rep-controls">
        <label>Window
          <select value={minutes} onChange={(e) => { setMinutes(Number(e.target.value)); loadReplay(Number(e.target.value)) }}>
            <option value="60">1 hr</option>
            <option value="180">3 hr</option>
            <option value="1440">24 hr</option>
          </select>
        </label>
        <button onClick={() => { setPlaying(!playing); if (!playing && frame === samples.length - 1) setFrame(0) }}>
          {playing ? '⏸ Pause' : '▶ Play'}
        </button>
        <button onClick={() => setFrame(samples.length - 1)}>⏭ End</button>
      </div>

      <div className="rep-chart">
        {visible.length === 0 ? (
          <p>No CO2 data in this window yet.</p>
        ) : (
          <>
            <div className="rep-line">
              {visible.map((s, i) => {
                const h = Math.min(120, Math.max(10, (s.co2 - 350) / 25))
                return <div key={i} className="rep-bar" style={{ height: `${h}px` }}
                  title={`${s.ts} · ${s.co2} ppm`} />
              })}
            </div>
          </>
        )}
      </div>

      <div className="rep-events">
        {events.filter((ev) => {
          const t = new Date(ev.ts).getTime()
          const t0 = new Date(replay.samples[0]?.ts || ev.ts).getTime()
          const tN = new Date(replay.samples[replay.samples.length - 1]?.ts || ev.ts).getTime()
          return t >= t0 && t <= tN
        }).map((ev, i) => (
          <span key={i} className={`ev ${ev.type}`}>
            {ev.type === 'alert' ? '🔴 Alert' : '💨 Vent'} · {new Date(ev.ts).toLocaleTimeString()} · {ev.detail}
          </span>
        ))}
      </div>

      <hr />

      <h4>Scenario Simulation (what-if)</h4>
      <div className="sim-form">
        <label>People
          <input type="number" min="0" placeholder={room.occupants ?? 0}
            onChange={(e) => simOcc.current = e.target.value} />
        </label>
        <label>Ventilation
          <select onChange={(e) => simVent.current = e.target.value === 'ON'}>
            <option value="OFF">OFF</option>
            <option value="ON">ON</option>
          </select>
        </label>
        <label>Target ppm
          <input type="number" placeholder="auto" onChange={(e) => simTarget.current = e.target.value} />
        </label>
        <button onClick={runSim}>Predict</button>
      </div>
      {sim && (
        <div className="sim-result">
          <p>Current CO2: <b>{sim.currentCo2}</b> ppm · {sim.occupants} people · vent {sim.ventilationOn ? 'ON' : 'OFF'}</p>
          <p>Reaches {sim.targetPpm} ppm: <b>{sim.reachLimitType === 'never' ? 'Never under these conditions' :
            sim.reachLimitType === 'already' ? 'Already at/above limit' : `in ~${sim.minutesToLimit.toFixed(1)} min`}</b></p>
        </div>
      )}
    </div>
  )
}
