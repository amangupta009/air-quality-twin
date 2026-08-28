import { useEffect, useRef, useState } from 'react'
import { fetchRooms, fetchReadings, setVentilation, renameRoom, setOccupancy, login, logout, setAuthToken } from './api'
import { playAlertBeep } from './sound'
import LoginGate from './components/LoginGate.jsx'
import RoomCard from './components/RoomCard.jsx'
import CalibrationScreen from './components/CalibrationScreen.jsx'
import InnovationPanel from './components/InnovationPanel.jsx'

function worstStatus(rooms) {
  if (rooms.some((r) => r.status === 'ALERT')) return 'ALERT'
  if (rooms.some((r) => r.status === 'WARNING')) return 'WARNING'
  return 'OK'
}

const STATUS_WORD = { OK: 'SAFE', WARNING: 'WARNING', ALERT: 'ALERT' }
const MAX_POINTS = 120

export default function App() {
  const [user, setUser] = useState(() => {
    const stored = localStorage.getItem('user')
    return stored ? JSON.parse(stored) : null
  })
  const [dark, setDark] = useState(() => localStorage.getItem('theme') === 'dark')
  const [rooms, setRooms] = useState([])
  const [points, setPoints] = useState({})
  const [error, setError] = useState(null)
  const seeded = useRef(false)
  const [showAdmin, setShowAdmin] = useState(false)
  const [showInnovation, setShowInnovation] = useState(false)

  const isAdmin = user?.role === 'ADMIN'
  const canControl = user?.role === 'ADMIN' || user?.role === 'FACILITY_MANAGER'

  useEffect(() => {
    if (!user) return

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
  }, [user])

  const pageStatus = worstStatus(rooms).toLowerCase()
  const prevStatus = useRef('ok')
  useEffect(() => {
    if (pageStatus === 'alert' && prevStatus.current !== 'alert') {
      playAlertBeep()
    }
    prevStatus.current = pageStatus
  }, [pageStatus])

  async function handleLogin(username, password) {
    const res = await login(username, password)
    setAuthToken(res.token)
    const session = { username: res.username, role: res.role, token: res.token }
    localStorage.setItem('user', JSON.stringify(session))
    setUser(session)
    return res
  }

  function handleLogout() {
    logout()
    localStorage.removeItem('user')
    setUser(null)
    setRooms([])
    setPoints({})
    seeded.current = false
  }

  if (!user) {
    return <LoginGate onLogin={handleLogin} />
  }

  async function handleVentilation(roomId, action) {
    try {
      await setVentilation(roomId, action, user.username, 'from dashboard')
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

  async function handleRename(roomId, name) {
    try {
      await renameRoom(roomId, name)
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
        <span className="chip">{user.username} · {user.role}</span>
        <span className="spacer" />
        <button className="ghost" onClick={toggleTheme}>{dark ? 'Light mode' : 'Dark mode'}</button>
        {isAdmin && <button className="ghost" onClick={() => { setShowAdmin(!showAdmin); setShowInnovation(false) }}>{showAdmin ? 'Dashboard' : 'Admin'}</button>}
        {canControl && <button className="ghost" onClick={() => { setShowInnovation(!showInnovation); setShowAdmin(false) }}>{showInnovation ? 'Dashboard' : 'Replay/Sim'}</button>}
        <button className="ghost" onClick={handleLogout}>Logout</button>
      </div>

      {error && <p className="error">Backend unreachable: {error}</p>}

      {isAdmin && showAdmin && rooms[0] ? (
        <CalibrationScreen rooms={rooms} user={user} />
      ) : showInnovation && rooms[0] ? (
        <InnovationPanel room={rooms[0]} user={user} />
      ) : (
        <>
          <div className="big-status">{STATUS_WORD[worstStatus(rooms)]}</div>
          <div className="grid">
            {rooms.map((room) => (
              <RoomCard
                key={room.roomId}
                room={room}
                points={points[room.roomId] || []}
                canControl={canControl}
                onVentilation={handleVentilation}
                onOccupancy={handleOccupancy}
                onRename={handleRename}
              />
            ))}
            {!rooms.length && !error && <p>Waiting for data…</p>}
          </div>
        </>
      )}
    </main>
  )
}
