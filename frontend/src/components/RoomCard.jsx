import { useState } from 'react'
import { LineChart, Line, XAxis, YAxis, Tooltip, CartesianGrid } from 'recharts'

export default function RoomCard({ room, points, onVentilation, onOccupancy }) {
  const [occDraft, setOccDraft] = useState('')
  const chartData = points.map((p) => ({ time: p.time, co2: p.co2 }))

  function submitOccupancy(e) {
    e.preventDefault()
    const val = Number(occDraft)
    if (val >= 0 && val <= 500) {
      onOccupancy(room.roomId, Math.round(val))
      setOccDraft('')
    }
  }

  return (
    <section className={`card ${room.status.toLowerCase()}`}>
      <header className="card-head">
        <h2>{room.roomName ?? room.roomId}</h2>
        <span className="occ-chip">{room.occupants ?? '—'} inside</span>
      </header>

      <div className="tiles">
        <div className="tile t-co2">
          <span className="label">CO2</span>
          <span className="value">{room.co2Ppm != null ? `${room.co2Ppm} ppm` : '—'}</span>
        </div>
        <div className="tile t-pm">
          <span className="label">PM2.5</span>
          <span className="value">{room.pm25 != null ? `${room.pm25} µg/m³` : '—'}</span>
        </div>
        <div className="tile t-vent">
          <span className="label">Ventilation</span>
          <span className="value">{room.ventilationOn ? 'ON' : 'OFF'}</span>
        </div>
        <div className="tile t-status">
          <span className="label">Status</span>
          <span className="value">{room.status}</span>
        </div>
      </div>

      <div className="chart-box">
        <LineChart data={chartData} margin={{ top: 8, right: 12, bottom: 0, left: -18 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#d5dbe2" />
          <XAxis dataKey="time" tick={{ fontSize: 10 }} tickCount={5} />
          <YAxis domain={[350, 'auto']} tick={{ fontSize: 10 }} />
          <Tooltip contentStyle={{ fontSize: 12 }} />
          <Line type="monotone" dataKey="co2" stroke="#2563eb" strokeWidth={2} dot={false} isAnimationActive={false} />
        </LineChart>
      </div>

      <p className="recommendation">{room.recommendation}</p>

      <div className="occupancy-row">
        <form className="occupancy-form" onSubmit={submitOccupancy}>
          <span className="occ-label">People in room</span>
          <input
            type="number"
            min="0"
            max="500"
            placeholder={room.occupants ?? 0}
            value={occDraft}
            onChange={(e) => setOccDraft(e.target.value)}
          />
          <button type="submit" disabled={occDraft === ''}>Set</button>
        </form>
      </div>

      <footer>
        <button onClick={() => onVentilation(room.roomId, 'ON')} disabled={room.ventilationOn}>
          Ventilation ON
        </button>
        <button onClick={() => onVentilation(room.roomId, 'OFF')} disabled={!room.ventilationOn}>
          Ventilation OFF
        </button>
      </footer>
    </section>
  )
}
