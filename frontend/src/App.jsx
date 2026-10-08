import { useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'
import { fetchRooms, fetchReadings, setVentilation, setOccupancy, login, logout, setAuthToken, enterRoom, createRoom } from './api'
import { playAlertBeep } from './sound'
import LoginGate from './components/LoginGate.jsx'
import RoomSelect from './components/RoomSelect.jsx'
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

const SESSION_VERSION = 2

export default function App() {
  const [user, setUser] = useState(() => {
    const stored = localStorage.getItem('user')
    if (!stored) return null
    try {
      const u = JSON.parse(stored)
      // Old sessions (from before the login -> room-select flow) are invalidated
      // so the user is always forced through the current login flow.
      if (u.sessionVersion !== SESSION_VERSION) {
        localStorage.removeItem('user')
        localStorage.removeItem('token')
        return null
      }
      return u
    } catch {
      localStorage.removeItem('user')
      return null
    }
  })
  const [dark, setDark] = useState(() => localStorage.getItem('theme') === 'dark')
  const [rooms, setRooms] = useState([])
  const [points, setPoints] = useState({})
  const [error, setError] = useState(null)
  const seeded = useRef(false)
  const roomsRef = useRef([])
  const lastPointAt = useRef({})   // per-room throttle for chart appends
  const [showAdmin, setShowAdmin] = useState(false)
  const [showInnovation, setShowInnovation] = useState(false)

  const isAdmin = user?.role === 'ADMIN'
  const canControl = user?.role === 'ADMIN' || user?.role === 'FACILITY_MANAGER'

  useEffect(() => {
    if (!user?.roomId) return
    const entered = (user.roomId || '').trim().toLowerCase()

    // ONE chart point per room per ~1.5s, no matter how many triggers fire.
    // The backend broadcasts the full twin state on EVERY ingest, so co2,
    // temp AND humidity messages each carry co2Ppm — plus polling adds its
    // own copy. Without this throttle the chart got 3-4 duplicate points per
    // 2s reading, which looked like "value frozen, then a sudden jump".
    function pushPoint(roomId, co2) {
      const now = Date.now()
      if (now - (lastPointAt.current[roomId] || 0) < 1500) return
      lastPointAt.current[roomId] = now
      setPoints((prev) => {
        const arr = [...(prev[roomId] || [])]
        arr.push({ time: new Date().toLocaleTimeString(), co2 })
        while (arr.length > MAX_POINTS) arr.shift()
        return { ...prev, [roomId]: arr }
      })
    }

    function absorb(data) {
      roomsRef.current = data
      setRooms(data)
      for (const r of data) {
        if (r.co2Ppm != null) pushPoint(r.roomId, r.co2Ppm)
      }
    }

    function load() {
      fetchRooms()
        .then((all) => {
          const data = all.filter((r) =>
            (r.roomId || '').trim().toLowerCase() === entered ||
            (r.roomName || '').trim().toLowerCase() === entered
          )
          if (data.length === 0) {
            setError(`Room "${user.roomId}" not found. Try: ${all.map((r) => r.roomId).join(', ')}`)
          } else {
            setError(null)
          }
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
        .catch((e) => {
          // Transient: the backend may be restarting. Keep the last known data
          // on screen and retry on the next tick instead of showing a red error.
          // Only show a gentle hint when we have nothing to display yet.
          const msg = String(e?.message || '').toLowerCase()
          const isNetwork = msg.includes('networkerror') || msg.includes('failed to fetch')
            || msg.includes('502') || msg.includes('503') || msg.includes('504') || msg.includes('500')
          if (isNetwork) {
            if (!roomsRef.current.length) setError('Connecting to backend… (retrying)')
            return
          }
          if (msg.includes('401') || msg.includes('403')) {
            handleLogout()
            return
          }
          setError(e.message)
        })
    }

    load()
    const timer = setInterval(load, 2000) // faster polling: ≤2s worst-case lag

    // Real-time live push (the WebSocket arrow in the data-flow diagram):
    // the backend broadcasts /topic/rooms/{id} the moment a reading is
    // ingested, so the dashboard updates in milliseconds instead of waiting
    // for the next poll tick. Polling stays as a fallback.
    function applyLive(snap) {
      if (!snap || snap.co2Ppm == null) return
      const roomId = snap.roomId
      const prev = (roomsRef.current || []).find((r) => r.roomId === roomId)
      const merged = { ...(prev || {}), ...snap, roomName: prev?.roomName || snap.roomName }
      const nextRooms = roomsRef.current
        .map((r) => (r.roomId === roomId ? merged : r))
        .filter(Boolean)
      if (!nextRooms.some((r) => r.roomId === roomId)) nextRooms.push(merged)
      roomsRef.current = nextRooms
      setRooms(nextRooms)
      pushPoint(roomId, snap.co2Ppm)
    }

    const ws = new Client({
      brokerURL: `ws://${window.location.host}/ws`,
      reconnectDelay: 3000,
      onConnect: () => {
        ws.subscribe(`/topic/rooms/${entered}`, (msg) => {
          try {
            applyLive(JSON.parse(msg.body))
          } catch {
            /* ignore malformed push */
          }
        })
      },
    })
    ws.activate()

    return () => {
      clearInterval(timer)
      ws.deactivate()
    }
  }, [user])

  const pageStatus = worstStatus(rooms).toLowerCase()
  const prevStatus = useRef('ok')
  useEffect(() => {
    // Alert aate hi ek baar beep (bilkul original behavior):
    // sirf jab status 'ok'->'alert' hota hai tab bajti hai, repeat nahi.
    if (pageStatus === 'alert' && prevStatus.current !== 'alert') {
      playAlertBeep()
    }
    prevStatus.current = pageStatus
  }, [pageStatus])

  async function handleLogin(username, password) {
    const res = await login(username, password)
    setAuthToken(res.token)
    const session = { username: res.username, role: res.role, token: res.token, roomId: null, sessionVersion: SESSION_VERSION }
    localStorage.setItem('user', JSON.stringify(session))
    setUser(session)
    return res
  }

  async function handleSelectRoom(id) {
    const res = await enterRoom(id, user.username)
    const entered = res.roomId || id
    const session = { ...user, roomId: entered }
    localStorage.setItem('user', JSON.stringify(session))
    setUser(session)
  }

  async function handleCreateRoom(name) {
    const id = name.trim().toLowerCase().replace(/\s+/g, '-').replace(/[^a-z0-9-]/g, '')
    await createRoom(id, name.trim())
    return id
  }

  function handleLogout() {
    logout()
    localStorage.removeItem('user')
    setUser(null)
    setRooms([])
    setPoints({})
    seeded.current = false
  }

  if (!user || !user.roomId) {
    if (!user) return <LoginGate onLogin={handleLogin} />
    return <RoomSelect user={user} onSelectRoom={handleSelectRoom} onCreateRoom={handleCreateRoom} />
  }

  async function handleVentilation(roomId, action) {
    try {
      await setVentilation(roomId, action, user.username, 'from dashboard')
      setRooms((await fetchRooms()).filter((r) => r.roomId === user.roomId))
    } catch (e) {
      setError(e.message)
    }
  }

  async function handleOccupancy(roomId, occupants) {
    try {
      await setOccupancy(roomId, occupants)
      setRooms((await fetchRooms()).filter((r) => r.roomId === user.roomId))
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
        <button className="ghost" onClick={() => { setShowInnovation(!showInnovation); setShowAdmin(false) }}>{showInnovation ? 'Dashboard' : 'Activity'}</button>
        <button className="ghost" onClick={handleLogout}>Logout</button>
      </div>

      {error && <p className="error">⚠ {error}</p>}

      {isAdmin && showAdmin && rooms[0] ? (
        <CalibrationScreen rooms={rooms} user={user} />
      ) : showInnovation && rooms[0] ? (
        <InnovationPanel room={rooms[0]} user={user} canControl={canControl} />
      ) : (
        <>
          <div className="big-status"><span className="dot" />{STATUS_WORD[worstStatus(rooms)]}</div>
            <div className="grid">
            {rooms.map((room) => (
              <RoomCard
                key={room.roomId}
                room={room}
                points={points[room.roomId] || []}
                canControl={canControl}
                onVentilation={handleVentilation}
                onOccupancy={handleOccupancy}
              />
            ))}
            {!rooms.length && !error && <p>Waiting for data…</p>}
            </div>
        </>
      )}
    </main>
  )
}
