import { useState } from 'react'
import { saveCalibration } from '../api'

// Admin screen: set scale + offset correction for each sensor.
// calibrated = raw * scale + offset
export default function CalibrationScreen({ rooms, user }) {
  const [roomId, setRoomId] = useState(rooms[0]?.roomId || 'room101')
  const [metric, setMetric] = useState('co2')
  const [scale, setScale] = useState('1.0')
  const [offset, setOffset] = useState('0.0')
  const [notes, setNotes] = useState('')
  const [saved, setSaved] = useState('')

  async function submit(e) {
    e.preventDefault()
    setSaved('')
    try {
      await saveCalibration({
        roomId,
        metric,
        offsetValue: Number(offset),
        scaleValue: Number(scale),
        calibratedBy: user.username,
        notes: notes || 'manual calibration',
      })
      setSaved(`Saved calibration for ${roomId} / ${metric}`)
    } catch (err) {
      setSaved(`Error: ${err.message}`)
    }
  }

  return (
    <div className="panel">
      <h3>Sensor Calibration</h3>
      <p className="panel-sub">Correct raw readings: <code>calibrated = raw × scale + offset</code></p>
      <form className="calib-form" onSubmit={submit}>
        <label>
          Room
          <select value={roomId} onChange={(e) => setRoomId(e.target.value)}>
            {rooms.map((r) => (
              <option key={r.roomId} value={r.roomId}>{r.roomName ?? r.roomId}</option>
            ))}
          </select>
        </label>
        <label>
          Metric
          <select value={metric} onChange={(e) => setMetric(e.target.value)}>
            <option value="co2">CO2</option>
          </select>
        </label>
        <label>
          Scale
          <input type="number" step="0.001" value={scale} onChange={(e) => setScale(e.target.value)} />
        </label>
        <label>
          Offset
          <input type="number" step="0.1" value={offset} onChange={(e) => setOffset(e.target.value)} />
        </label>
        <label>
          Notes
          <input value={notes} placeholder="e.g. adjusted against reference" onChange={(e) => setNotes(e.target.value)} />
        </label>
        <button type="submit">Save Calibration</button>
      </form>
      {saved && <p className="calib-result">{saved}</p>}
    </div>
  )
}
