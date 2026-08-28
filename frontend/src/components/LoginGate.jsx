import { useState } from 'react'

// Login with role-based credentials (viewer / manager / admin).
// Default demo accounts:
//   viewer  / viewer123   (read-only)
//   manager / manager123  (ventilation + occupancy control)
//   admin   / admin123    (everything incl. calibration, settings)
export default function LoginGate({ onLogin }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')

  const valid = username.trim().length >= 2 && password.length >= 4

  async function submit(e) {
    e.preventDefault()
    if (!valid) return
    setError('')
    try {
      const res = await onLogin(username.trim(), password)
      if (!res) return
    } catch (err) {
      setError(err.message || 'Login failed')
    }
  }

  return (
    <main className="login-page">
      <form className="login-box" onSubmit={submit}>
        <h1>Air Quality Digital Twin</h1>
        <p>Sign in to start monitoring</p>

        <label>
          Username
          <input
            autoFocus
            placeholder="e.g. admin"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
          />
        </label>

        <label>
          Password
          <input
            type="password"
            placeholder="••••••••"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>

        {error && <p className="login-error">{error}</p>}

        <button type="submit" disabled={!valid}>
          Sign In
        </button>

        <div className="login-hint">
          <p>Demo accounts</p>
          <p>viewer / viewer123</p>
          <p>manager / manager123</p>
          <p>admin / admin123</p>
        </div>
      </form>
    </main>
  )
}
