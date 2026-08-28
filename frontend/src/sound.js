// Short beep triplett via Web Audio API - no audio files needed.
// Browsers only allow sound after a user gesture (e.g. after login click).
let ctx

export function playAlertBeep() {
  try {
    ctx = ctx || new (window.AudioContext || window.webkitAudioContext)()
    ;[0, 0.35, 0.7].forEach((offset) => {
      const osc = ctx.createOscillator()
      const gain = ctx.createGain()
      osc.type = 'square'
      osc.frequency.value = 880
      gain.gain.value = 0.07
      osc.connect(gain)
      gain.connect(ctx.destination)
      const t = ctx.currentTime + offset
      osc.start(t)
      osc.stop(t + 0.22)
    })
  } catch {
    // audio not available - silent fallback, never crash the dashboard
  }
}
