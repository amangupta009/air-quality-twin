// Thin wrapper around fetch so components stay readable.
// All data flows through the backend REST API - never directly to the DB.

let authToken = localStorage.getItem('token') || ''

export function setAuthToken(token) {
  authToken = token
  if (token) localStorage.setItem('token', token)
  else localStorage.removeItem('token')
}

export function getAuthToken() {
  return authToken
}

async function handle(response) {
  if (!response.ok) {
    const msg = await response.text().catch(() => '')
    throw new Error(`API ${response.status}: ${msg || response.statusText}`)
  }
  return response.json()
}

function authHeaders(body) {
  const headers = authToken ? { Authorization: `Bearer ${authToken}` } : {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  return headers
}

export function login(username, password) {
  return fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  }).then(handle)
}

export function logout() {
  if (authToken) {
    fetch('/api/auth/logout', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify({ token: authToken }),
    }).catch(() => {})
  }
  setAuthToken('')
}

export function fetchRooms() {
  return fetch('/api/rooms').then(handle)
}

export function setVentilation(roomId, action, actor = 'facility-manager', note = '') {
  return fetch(`/api/rooms/${roomId}/ventilation`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ action, actor, note }),
  }).then(handle)
}

export function renameRoom(roomId, name) {
  return fetch(`/api/rooms/${roomId}/name`, {
    method: 'PATCH',
    headers: authHeaders(),
    body: JSON.stringify({ name }),
  }).then(handle)
}

export function setOccupancy(roomId, occupants) {
  return fetch(`/api/rooms/${roomId}/occupancy`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ occupants }),
  }).then(handle)
}

export function fetchReadings(roomId, minutes = 15) {
  return fetch(`/api/rooms/${roomId}/readings?minutes=${minutes}`).then(handle)
}

export function fetchThresholds() {
  return fetch('/api/settings/thresholds').then(handle)
}

export function updateThresholds(co2Ppm) {
  return fetch('/api/settings/thresholds', {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify({ co2Ppm }),
  }).then(handle)
}

export function fetchCalibration(roomId) {
  return fetch(`/api/calibration/${roomId}`).then(handle)
}

export function saveCalibration(data) {
  return fetch('/api/calibration', {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(data),
  }).then(handle)
}

export function fetchDailySummary(roomId, date = '') {
  const q = date ? `?date=${date}` : ''
  return fetch(`/api/summary/daily/${roomId}${q}`).then(handle)
}

export function fetchReplay(roomId, minutes = 60) {
  return fetch(`/api/replay/${roomId}?minutes=${minutes}`).then(handle)
}

export function simulateScenario(roomId, occupants, ventilationOn, targetPpm = null) {
  return fetch(`/api/simulate/${roomId}`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ occupants, ventilationOn, targetPpm }),
  }).then(handle)
}
