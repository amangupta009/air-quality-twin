import { useState } from 'react'

// Operator sign-in: name is used for the audit trail, the room name labels
// the dashboard, and the head-count drives how fast CO2 rises in the sim.
export default function LoginGate({ onEnter }) {
  const [name, setName] = useState('')
  const [roomName, setRoomName] = useState('')
  const [people, setPeople] = useState('')

  const valid =
    name.trim().length >= 2 &&
    roomName.trim().length >= 2 &&
    people !== '' &&
    Number(people) >= 0

  function submit(e) {
    e.preventDefault()
    if (valid) {
      onEnter({
        name: name.trim(),
        roomName: roomName.trim(),
        people: Math.max(0, Math.round(Number(people))),
      })
    }
  }

  return (
    <main className="login-page">
      <form className="login-box" onSubmit={submit}>
        <h1>Air Quality Digital Twin</h1>
        <p>Enter facility details to start monitoring</p>

        <label>
          Your name
          <input
            autoFocus
            placeholder="e.g. Aman"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </label>

        <label>
          Room name
          <input
            placeholder="e.g. Physics Lab"
            value={roomName}
            onChange={(e) => setRoomName(e.target.value)}
          />
        </label>

        <label>
          No. of people in room
          <input
            type="number"
            min="0"
            max="500"
            placeholder="e.g. 6"
            value={people}
            onChange={(e) => setPeople(e.target.value)}
          />
        </label>

        <button type="submit" disabled={!valid}>
          Start Monitoring
        </button>
      </form>
    </main>
  )
}
