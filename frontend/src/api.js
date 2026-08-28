// Thin wrapper around fetch so components stay readable.
// All data flows through the backend REST API - never directly to the DB.

async function handle(response) {
  if (!response.ok) {
    throw new Error(`API ${response.status}: ${response.statusText}`)
  }
  return response.json()
}

export function fetchRooms() {
  return fetch('/api/rooms').then(handle)
}

export function setVentilation(roomId, action, actor = 'facility-manager', note = '') {
  return fetch(`/api/rooms/${roomId}/ventilation`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ action, actor, note }),
  }).then(handle)
}

export function renameRoom(roomId, name) {
  return fetch(`/api/rooms/${roomId}/name`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  }).then(handle)
}

export function setOccupancy(roomId, occupants) {
  return fetch(`/api/rooms/${roomId}/occupancy`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ occupants }),
  }).then(handle)
}

export function setCo2(roomId, co2Ppm) {
  return fetch(`/api/rooms/${roomId}/co2`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ co2Ppm }),
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
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ co2Ppm }),
  }).then(handle)
}
