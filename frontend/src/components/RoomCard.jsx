import { useState } from 'react'
import { LineChart, Line, Area, XAxis, YAxis, Tooltip, CartesianGrid, ResponsiveContainer } from 'recharts'

export default function RoomCard({ room, points, canControl, onVentilation, onOccupancy }) {
  const [occDraft, setOccDraft] = useState('')
  const chartData = points.map((p) => ({ time: p.time, co2: p.co2 }))
  const status = room.status || 'OK'
  const statusWord = status === 'OK' ? 'SAFE' : status
  const statusTag = status === 'ALERT' ? 'Above safe limit'
    : status === 'WARNING' ? 'Approaching limit'
    : 'Normal range'

  function submitOccupancy(e) {
    e.preventDefault()
    const val = Number(occDraft)
    if (val >= 0 && val <= 500) {
      onOccupancy(room.roomId, Math.round(val))
      setOccDraft('')
    }
  }

  return (
    <section className={`dash dash-${status.toLowerCase()}`}>
      <header className="dash-head">
        <div className="dash-title">
          <span className="dash-icon">🏢</span>
          <div>
            <h2>{room.roomName ?? room.roomId}</h2>
            <span className="dash-sub">{room.roomId}</span>
          </div>
        </div>
        <div className="dash-status">
          <span className="dash-status-dot" />
          <span className="dash-status-txt">{statusWord}</span>
        </div>
      </header>

      <div className="dash-tiles">
        <div className="dtile dt-co2">
          <span className="dt-label">CO₂</span>
          <span className="dt-value">{room.co2Ppm != null ? room.co2Ppm : '—'}<small>ppm</small></span>
          <span className={`dt-ta dt-${status.toLowerCase()}`}>{statusTag}</span>
        </div>
        <div className="dtile dt-temp">
          <span className="dt-label">Temperature</span>
          <span className="dt-value">{room.temperature != null ? room.temperature : '—'}<small>°C</small></span>
        </div>
        <div className="dtile dt-hum">
          <span className="dt-label">Humidity</span>
          <span className="dt-value">{room.humidity != null ? room.humidity : '—'}<small>%</small></span>
        </div>
        <div className="dtile dt-vent">
          <span className="dt-label">Ventilation</span>
          <span className={`dt-value dt-vent-on ${room.ventilationOn ? 'on' : ''}`}>{room.ventilationOn ? 'ON' : 'OFF'}</span>
        </div>
        <div className="dtile dt-occ">
          <span className="dt-label">Occupancy</span>
          <span className="dt-value">{room.occupants ?? '—'}<small>people</small></span>
        </div>
      </div>

      <div className="dash-chart">
        <div className="dash-chart-head">
          <span>CO₂ trend (live)</span>
          <span className="dash-chart-now">{room.co2Ppm != null ? `${room.co2Ppm} ppm now` : '—'}</span>
        </div>
        <div className="chart-box">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={chartData} margin={{ top: 6, right: 10, bottom: 0, left: -20 }}>
              <defs>
                <linearGradient id="co2Fill" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#3b82f6" stopOpacity={0.35} />
                  <stop offset="100%" stopColor="#3b82f6" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#d5dbe2" vertical={false} />
              <XAxis dataKey="time" tick={{ fontSize: 9 }} tickCount={4} axisLine={false} tickLine={false} />
              <YAxis domain={[350, 'auto']} tick={{ fontSize: 9 }} axisLine={false} tickLine={false} width={40} />
              <Tooltip contentStyle={{ fontSize: 11, borderRadius: 8 }} />
              <Area type="monotone" dataKey="co2" stroke="none" fill="url(#co2Fill)" isAnimationActive={false} />
              <Line type="monotone" dataKey="co2" stroke="#2563eb" strokeWidth={2.5} dot={false} isAnimationActive={false} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </div>

      <p className="dash-reco">💡 {room.recommendation}</p>

      <div className="dash-controls">
        <div className="dash-occ">
          <form className="dash-occ-form" onSubmit={submitOccupancy}>
            <span className="dash-occ-label">👥 People</span>
            <input
              type="number"
              min="0"
              max="500"
              placeholder={room.occupants ?? 0}
              value={occDraft}
              onChange={(e) => setOccDraft(e.target.value)}
              disabled={!canControl}
            />
            <button type="submit" className="dash-occ-set" disabled={occDraft === '' || !canControl}>Set</button>
          </form>
        </div>
        <div className="dash-vent">
          <button className="dash-von" onClick={() => onVentilation(room.roomId, 'ON')}
            disabled={room.ventilationOn || !canControl}>Ventilation ON</button>
          <button className="dash-voff" onClick={() => onVentilation(room.roomId, 'OFF')}
            disabled={!room.ventilationOn || !canControl}>Ventilation OFF</button>
        </div>
      </div>
    </section>
  )
}
