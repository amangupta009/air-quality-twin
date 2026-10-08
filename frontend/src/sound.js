// Short beep triplett via Web Audio API - no audio files needed.
// Browsers only allow sound after a user gesture (e.g. after login click).
// No UI controls: the beep fires once the moment an alert arrives and that's
// it - exactly like the original prototype.
//
// Reliability details (why this file looks like this):
//  - The AudioContext is created on the FIRST user gesture of the page load,
//    not lazily when the alert arrives. A hard refresh (Ctrl+Shift+R) clears
//    the browser's user activation, so a lazily-created context stays
//    suspended if an alert arrives before the first click - and the beep is
//    silently lost (this actually happened).
//  - If an alert still lands while audio is suspended (fresh load, no click
//    yet, hidden tab), the beep is remembered and delivered on the first
//    gesture or when the tab comes back - late, but never lost.
let ctx
let pending = false

function scheduleBeep(audio) {
  ;[0, 0.35, 0.7].forEach((offset) => {
    const osc = audio.createOscillator()
    const gain = audio.createGain()
    osc.type = 'square'
    osc.frequency.value = 880
    gain.gain.value = 0.4
    osc.connect(gain)
    gain.connect(audio.destination)
    const t = audio.currentTime + offset
    osc.start(t)
    osc.stop(t + 0.22)
  })
}

function ensureCtx() {
  ctx = ctx || new (window.AudioContext || window.webkitAudioContext)()
  return ctx
}

export function playAlertBeep() {
  try {
    const audio = ensureCtx()
    if (audio.state !== 'running') {
      // No user gesture yet in this page load: browsers keep audio suspended.
      // Remember the beep and play it at the first gesture (see wake below).
      pending = true
      if (audio.state === 'suspended') audio.resume().catch(() => {})
      return
    }
    scheduleBeep(audio)
  } catch {
    // audio not available - silent fallback, never crash the dashboard
  }
}

// Create (during the gesture, so the browser allows it) AND re-arm the
// AudioContext on any user gesture, then deliver a beep that arrived while
// audio was still suspended. Invisible: no buttons, no banners, same
// one-beep-per-alert behavior.
const wake = () => {
  try {
    const audio = ensureCtx()
    if (audio.state === 'suspended') {
      audio
        .resume()
        .then(() => {
          if (pending) {
            pending = false
            scheduleBeep(audio)
          }
        })
        .catch(() => {})
    } else if (pending) {
      pending = false
      scheduleBeep(audio)
    }
  } catch {
    // audio not available - stay silent
  }
}
window.addEventListener('pointerdown', wake)
window.addEventListener('keydown', wake)
document.addEventListener('visibilitychange', () => {
  if (!document.hidden) wake()
})
