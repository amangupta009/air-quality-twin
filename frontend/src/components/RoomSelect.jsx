import { useEffect, useState } from 'react'
import { fetchMetaRooms, createRoom } from '../api'

// Step 2 of login: choose a room.
//   - Everyone: pick an existing room to enter.
//   - ADMIN only: may also create a brand-new room (name is the id; spaces
//     become dashes). Managers/viewers get the pick-list only.
export default function RoomSelect({ user, onSelectRoom, onCreateRoom }) {
  const [rooms, setRooms] = useState([])
  const [newName, setNewName] = useState('')
  const [busy, setBusy] = useState(null)
  const [error, setError] = useState('')

  const isAdmin = user?.role === 'ADMIN'

  useEffect(() => {
    fetchMetaRooms()
      .then(setRooms)
      .catch(() => setRooms([]))
  }, [])

  async function pick(id) {
    setBusy(id)
    setError('')
    try {
      await onSelectRoom(id)
    } catch (err) {
      setError(err.message || 'Could not enter room')
      setBusy(null)
    }
  }

  async function create(e) {
    e.preventDefault()
    const name = newName.trim()
    if (name.length < 2) return
    setBusy('__create__')
    setError('')
    try {
      await onCreateRoom(name)
      setNewName('')
      const list = await fetchMetaRooms()
      setRooms(list)
      setBusy(null)
    } catch (err) {
      setError(err.message || 'Could not create room')
      setBusy(null)
    }
  }

  return (
    <main className="login-page">
      <div className="login-box room-select">
        <h1>Choose a room</h1>
        <p>Signed in as <b>{user.username}</b> ({user.role})</p>

        <div className="rs-section">
          <div className="rs-title">Existing rooms</div>
          {rooms.length === 0 ? (
            <p className="rs-empty">No rooms yet{isAdmin ? ' — create one below.' : '.'}</p>
          ) : (
            <div className="rs-list">
              {rooms.map((r) => (
                <button
                  key={r.id}
                  className="rs-room"
                  onClick={() => pick(r.id)}
                  disabled={busy === r.id}
                >
                  <span className="rs-name">{r.name}</span>
                  <span className="rs-id">{r.id}</span>
                </button>
              ))}
            </div>
          )}
        </div>

        {isAdmin && (
          <form className="rs-create" onSubmit={create}>
            <div className="rs-title">Create a new room (Admin only)</div>
            <div className="rs-create-row">
              <input
                placeholder="e.g. Lab 4  (spaces become dashes)"
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
              />
              <button type="submit" disabled={newName.trim().length < 2 || busy === '__create__'}>
                {busy === '__create__' ? 'Creating…' : 'Create'}
              </button>
            </div>
          </form>
        )}

        {!isAdmin && (
          <p className="rs-note">Only Admin can create new rooms.</p>
        )}

        {error && <p className="login-error">{error}</p>}
      </div>
    </main>
  )
}
