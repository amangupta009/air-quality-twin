import { useEffect, useRef, useState } from 'react'
import { fetchRooms, fetchReadings, setVentilation, renameRoom, setOccupancy } from './api'
import { playAlertBeep } from './sound'
import LoginGate from './components/LoginGate.jsx'
import RoomCard from './components/RoomCard.jsx'

function worstStatus(rooms) {
  if (rooms.some((r) => r.status === 'ALERT')) return 'ALERT'
  if (rooms.some((r) => r.status === 'WARNING')) return 'WARNING'
  return 'OK'
}

const STATUS_WORD = { OK: 'SAFE', WARNING: 'WARNING', ALERT: 'ALERT' }
const MAX_POINTS = 120

export default function App() {
  const [operator, setOperator] = useState(() => localStorage.getItem('operator') || '')
  const [dark, setDark] = useState(() => localStorage.getItem('theme') === 'dark')
  const [rooms, setRooms] = useState([])
  const [points, setPoints] = useState({})
  const [error, setError] = useState(null)
  const seeded = useRef(false)

  useEffect(() => {
    if (!operator) return

    function absorb(data) {
      setRooms(data)
      setPoints((prev) => {
        const next = { ...prev }
        for (const r of data) {
          if (r.co2Ppm == null) continue
          const arr = [...(next[r.roomId] || [])]
          arr.push({ time: new Date().toLocaleTimeString(), co2: r.co2Ppm })
          while (arr.length > MAX_POINTS) arr.shift()
          next[r.roomId] = arr
        }
        return next
      })
    }

    function load() {
      fetchRooms()
        .then((data) => {
          setError(null)
          if (!seeded.current) {
            seeded.current = true
            Promise.all(
              data.map((r) =>
                fetchReadings(r.roomId, 15)
                  .then((rs) => [
                    r.roomId,
                    rs.filter((x) => x.metric === 'co2')
                      .map((x) => ({ time: new Date(x.recordedAt).toLocaleTimeString(), co2: x.value })),
                  ])
                  .catch(() => [r.roomId, []]),
              ),
            ).then((entries) => setPoints(Object.fromEntries(entries)))
          }
          absorb(data)
        })
        .catch((e) => setError(e.message))
    }

    load()
    const timer = setInterval(load, 5000)
    return () => clearInterval(timer)
  }, [operator])

  const pageStatus = worstStatus(rooms).toLowerCase()
  const prevStatus = useRef('ok')
  useEffect(() => {
    if (pageStatus === 'alert' && prevStatus.current !== 'alert') {
      playAlertBeep()
    }
    prevStatus.current = pageStatus
  }, [pageStatus])

  if (!operator) {
    return (
      <LoginGate
        onEnter={async ({ name, roomName, people }) => {
          localStorage.setItem('operator', name)
          setOperator(name)
          try {
            const rs = await fetchRooms()
            if (rs.length) {
              await renameRoom(rs[0].roomId, roomName)
              await setOccupancy(rs[0].roomId, people)
              setRooms(await fetchRooms())
            }
          } catch (e) {
            setError(e.message)
          }
        }}
      />
    )
  }

  async function handleVentilation(roomId, action) {
    try {
      await setVentilation(roomId, action, operator, 'from dashboard')
      setRooms(await fetchRooms())
    } catch (e) {
      setError(e.message)
    }
  }

  async function handleOccupancy(roomId, occupants) {
    try {
      await setOccupancy(roomId, occupants)
      setRooms(await fetchRooms())
    } catch (e) {
      setError(e.message)
    }
  }

  function toggleTheme() {
    const next = !dark
    setDark(next)
    localStorage.setItem('theme', next ? 'dark' : 'light')
  }

  return (
    <main className={`page ${pageStatus} ${dark ? 'dark' : ''}`}>
      <div className="topbar">
        <span className="chip">Operator: <b>{operator}</b></span>
        <span className="spacer" />
        <button className="ghost" onClick={toggleTheme}>{dark ? 'Light mode' : 'Dark mode'}</button>
        <button className="ghost" onClick={() => { localStorage.removeItem('operator'); setOperator('') }}>
          Logout
        </button>
      </div>

      {error && <p className="error">Backend unreachable: {error}</p>}
      <div className="big-status">{STATUS_WORD[worstStatus(rooms)]}</div>

      <div className="grid">
        {rooms.map((room) => (
          <RoomCard
            key={room.roomId}
            room={room}
            points={points[room.roomId] || []}
            onVentilation={handleVentilation}
            onOccupancy={handleOccupancy}
          />
        ))}
        {!rooms.length && !error && <p>Waiting for data…</p>}
      </div>
    </main>
  )
}
