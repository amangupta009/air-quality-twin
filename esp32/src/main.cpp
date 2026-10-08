/*
 * ESP32 Air Quality Sensor Station (MQ-135 build)
 * -----------------------------------------------
 * Reads an approximate CO2 level from an MQ-135 analog sensor and
 * publishes it to the MQTT contract the backend expects:
 *
 *   sensors/{roomId}/co2     {"value": 512.4, "unit": "ppm", "ts": iso8601}
 *
 * MQ-135 is a low-cost analog chemiresistor. It is NOT a calibrated NDIR
 * sensor, so the ppm value is an estimate (rough room-trend indicator), not
 * lab-grade. See docs/ for the calibration hook in the backend.
 *
 * Wiring (breadboard):
 *   MQ-135 (common 4-pin module):
 *     VCC  -> ESP32 VIN (5V). A healthy module's AO then sits around
 *             0.5-2.5V (within ADC range). If a specific module clips at
 *             >3.3V, the firmware detects it and warns on serial — fix by
 *             adding a divider (10k from AO to GPIO34, 6.8k from GPIO34 to
 *             GND) or powering the module at 3.3V.
 *     GND  -> ESP32 GND
 *     AO   -> ESP32 GPIO34 (ADC1_CH6)
 *     DO   -> leave unconnected
 *
 * Dependencies (Arduino IDE / PlatformIO):
 *   - PubSubClient by Nick O'Leary
 *   - DHT sensor library by Adafruit (+ Adafruit Unified Sensor)
 *
 * Build with PlatformIO (recommended) - see platformio.ini in this folder.
 * Flash: pio run -t upload
 */

#include <WiFi.h>
#include <PubSubClient.h>
#include <DHT.h>

// ---------------------------------------------------------------------------
// CONFIGURATION - edit these for your environment
// ---------------------------------------------------------------------------
const char *WIFI_SSID     = "YOUR_WIFI_SSID";
const char *WIFI_PASS     = "YOUR_WIFI_PASSWORD";

const char *MQTT_BROKER   = "YOUR_BACKEND_LAN_IP";   // laptop/backend host LAN IP
const int   MQTT_PORT     = 1884;              // mosquitto dev listener port
const char *ROOM_ID       = "room101";         // must match backend room
const char *TOPIC_CO2     = "sensors/room101/co2";
const char *TOPIC_TEMP    = "sensors/room101/temperature";
const char *TOPIC_HUM     = "sensors/room101/humidity";

const int   READ_INTERVAL_MS = 2000;           // publish every 2s (dashboard refresh)

// GPIO wiring
const int MQ135_AO = 34;    // ESP32 GPIO34 (ADC1_CH6) reads MQ-135 analog out

// DHT11 (temperature + humidity), DIFFERENT pin from the MQ-135 (GPIO34).
// DevKitC 30-pin: right-side column, 5th pin from the top (3V3, GND, 15, 2, 4).
//   VCC  -> ESP32 3.3V
//   GND  -> ESP32 GND
//   DATA -> ESP32 GPIO4
const int DHT_PIN  = 4;
#define DHT_TYPE DHT11
DHT dht(DHT_PIN, DHT_TYPE);

const int WARMUP_MS    = 120000;  // 2min heater settle before trusting readings
const int SAMPLES      = 20;      // averaging samples per read
const int EMA_MS       = 30000;   // slow baseline adapt window for sensor drift
const int EMA_UP_MS    = 300000;  // slow UPWARD baseline self-heal (5 min tau):
                                  // absorbs a cold-start heater ramp that would
                                  // otherwise pin the baseline low forever

// If the module output collapses (e.g. 0.00x V from a weak/dead heater or a
// bad seed), baseline must never follow it below this floor, otherwise the
// ratio explosion prints a fake "2500 ppm" for clean air.
const float BASELINE_FLOOR = 0.03f;   // volts - hard minimum for the baseline
// ADC saturation threshold: analogRead() clips at 4095 == 3.3V. A reading at
// (or above) this voltage means the module output is out of ADC range — a
// 5V-powered module without a divider. Never trust/seed from a clipped value.
const float ADC_SAT_V = 3.28f;        // volts (just below the 3.3V ceiling)

// ---------------------------------------------------------------------------
WiFiClient espClient;
PubSubClient mqtt(espClient);

unsigned long lastRead = 0;
unsigned long bootTime = 0;
unsigned long lastBaselineUpdate = 0;
float baselineV = 0.0f;      // slow-moving baseline (assumed ~400 ppm air)

// Median filter: keeps the last 5 estimates. A single noisy ADC sample can no
// longer print as a fake "5000" reading — the median of the window is what
// gets published to the twin, so the dashboard shows the real trend.
const int   CO2_WIN = 5;
float       co2Window[CO2_WIN] = { 0 };
int         co2WinIdx = 0;
int         co2WinFill = 0;

// Voltage rate-limit: a real gas concentration changes over seconds/minutes,
// never as a 0V <-> 0.1V flip between two 2.5s reads. If the module output is
// unstable/floating, clamp the change so the dashboard shows one sane value
// instead of bouncing between 340 and 2500.
float lastGoodV = -1.0f;            // -1 = not yet set

// Display slew limit: cap how fast the PUBLISHED ppm may move per read
// (~120 ppm / 2s = 3600 ppm/min). The voltage rate-limit alone still allows a
// huge ppm step when the clean-air baseline is low (gain scales with
// 1/baseline), which made the number freeze for a few reads and then jump
// hundreds/thousands at once. Slew-limiting both directions renders real gas
// as a smooth ramp instead of a cliff. 0 = nothing published yet, so the
// first reading seeds unclamped.
float lastPublishedPpm = 0.0f;

// ---------------------------------------------------------------------------
float readAnalogVoltage() {
  long sum = 0;
  for (int i = 0; i < SAMPLES; i++) {
    sum += analogRead(MQ135_AO);
    delay(2);
  }
  // ESP32 ADC is 12-bit (0-4095) over 0-3.3V
  return (sum / (float)SAMPLES) * (3.3f / 4095.0f);
}

// ---------------------------------------------------------------------------
// MQ-135 has no digital ppm output. We map sensor voltage to a plausible CO2
// trend: higher gas concentration -> higher AO voltage on the module (active
// variant). We keep a slow-moving baseline of clean-air voltage so short-term
// variations (breathing near the sensor, room ventilation) push ppm up/down
// while long-term sensor drift is absorbed. This is deliberately an ESTIMATE.
float estimateCo2Ppm(float voltage, float baseline) {
  if (baseline <= 0.0f) return -1;
  float ratio = voltage / baseline;          // >1 when gas present (active-high)
  if (ratio < 1.0f) {
    // Below baseline: gentle slope downward, never below outdoor (~350ppm)
    return 400.0f - (1.0f - ratio) * 60.0f;
  }
  // Gentle response curve (exponent 1.5): a small gas hit shows a moderate
  // rise, a stronger hit a proportionally bigger one. No hard display cap:
  // the value tracks the real concentration instead of slamming to a ceiling.
  // (10000 is only a safety net for ADC noise, not a "limit" the user sees.)
  float ppm = 400.0f * pow(ratio, 1.5f);
  if (ppm > 10000.0f) ppm = 10000.0f;
  return ppm;
}

void updateBaseline(float voltage) {
  if (baselineV <= 0.0f) {
    baselineV = voltage;
    return;
  }
  float alpha = 1.0f - exp(-1.0f / (EMA_MS / READ_INTERVAL_MS));
  baselineV += (voltage - baselineV) * alpha;
}

// ---------------------------------------------------------------------------
/** Spike-proof value: median of the last CO2_WIN estimates (insertion sort). */
float medianCo2(float value) {
  co2Window[co2WinIdx] = value;
  co2WinIdx = (co2WinIdx + 1) % CO2_WIN;
  if (co2WinFill < CO2_WIN) co2WinFill++;

  float tmp[CO2_WIN];
  int n = co2WinFill;
  for (int i = 0; i < n; i++) tmp[i] = co2Window[i];
  for (int i = 1; i < n; i++) {          // insertion sort (n<=5, trivial cost)
    float key = tmp[i];
    int j = i - 1;
    while (j >= 0 && tmp[j] > key) { tmp[j + 1] = tmp[j]; j--; }
    tmp[j + 1] = key;
  }
  if (n == 1) return tmp[0];
  if (n % 2 == 1) return tmp[n / 2];
  return (tmp[n / 2 - 1] + tmp[n / 2]) / 2.0f;
}

// ---------------------------------------------------------------------------
void publishMetric(const char *topic, float value, const char *unit) {
  char ts[32];
  struct tm t;
  if (!getLocalTime(&t)) {
    snprintf(ts, sizeof(ts), "%lu", (unsigned long)millis());
  } else {
    snprintf(ts, sizeof(ts), "%04d-%02d-%02dT%02d:%02d:%02dZ",
             t.tm_year + 1900, t.tm_mon + 1, t.tm_mday,
             t.tm_hour, t.tm_min, t.tm_sec);
  }
  char payload[200];
  snprintf(payload, sizeof(payload),
           "{\"value\":%.1f,\"unit\":\"%s\",\"ts\":\"%s\"}", value, unit, ts);
  mqtt.publish(topic, payload);
}

// ---------------------------------------------------------------------------
void connectMQTT() {
  while (!mqtt.connected()) {
    if (mqtt.connect("esp32-station")) {
      Serial.println("MQTT connected");
    } else {
      Serial.print("MQTT failed, rc=");
      Serial.println(mqtt.state());
      delay(2000);
    }
  }
}

// ---------------------------------------------------------------------------
void setup() {
  Serial.begin(115200);
  pinMode(MQ135_AO, INPUT);
  analogReadResolution(12);
  dht.begin();

  WiFi.begin(WIFI_SSID, WIFI_PASS);
  Serial.print("Connecting WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWiFi connected, IP=");
  Serial.println(WiFi.localIP());

  mqtt.setServer(MQTT_BROKER, MQTT_PORT);
  configTime(0, 0, "pool.ntp.org");   // for timestamp

  connectMQTT();

  bootTime = millis();
}

// ---------------------------------------------------------------------------
void loop() {
  if (!mqtt.connected()) connectMQTT();
  mqtt.loop();

  if (millis() - lastRead < READ_INTERVAL_MS) return;   // wait for next slot
  lastRead = millis();

  // --- DHT11 temperature + humidity (displayed on the dashboard) ---
  // Read/publish immediately, independent of the CO2 heater warm-up, so the
  // dashboard shows temp/humidity from the first seconds after boot.
  float h = dht.readHumidity();
  float t = dht.readTemperature();
  if (isnan(h) || isnan(t)) {
    Serial.println("DHT11 read failed (check wiring / pull-up)");
  } else {
    publishMetric(TOPIC_TEMP, t, "celsius");
    publishMetric(TOPIC_HUM,  h, "percent");
    Serial.printf("DHT11: %.1f C, %.1f %%\n", t, h);
  }

  // --- MQ-135 CO2 (after heater warm-up) ---
  // AO ramps while the heater settles; trusting it early seeds a wrong
  // baseline. Print a countdown so the serial console is never silent.
  unsigned long uptime = millis() - bootTime;
  if (uptime < WARMUP_MS) {
    Serial.printf("[CO2] heater warming up... %ds left (temp/hum already live)\n",
                  (int)((WARMUP_MS - uptime) / 1000));
    return;
  }

  float v = readAnalogVoltage();

  // Clipped-signal guard: a reading riding the ADC ceiling means the module
  // output is >3.3V (5V-powered module without a divider). Skip the slot and
  // never seed the baseline from a clipped value — seeding it would make the
  // whole sensor blind. Serial makes the wiring issue obvious and actionable.
  if (v >= ADC_SAT_V) {
    Serial.printf("AO SATURATED %.3f V (ADC ceiling): module output >3.3V? "
                  "add 10k/6.8k divider or power at 3.3V\n", v);
    return;   // baseline untouched; retry next slot
  }

  // Rate-limit the raw voltage: clamp how far it can move per read so an
  // unstable/floating analog line cannot slam the estimate to the cap and
  // back. Real gas still tracks — it changes smoothly over several reads.
  if (lastGoodV < 0.0f) lastGoodV = v;
  float dv = v - lastGoodV;
  if (dv > 0.03f) dv = 0.03f;
  if (dv < -0.03f) dv = -0.03f;
  v = lastGoodV + dv;
  lastGoodV = v;

  // Baseline management:
  //  - first valid voltage seeds the baseline once;
  //  - baseline has a hard floor so a collapsed signal (0.00x V) can never
  //    explode into a fake ratio -> a fake "2500 ppm" reading;
  //  - baseline adapts ONLY when the air is near-clean (ratio < 1.3), so a
  //    gas exposure can never drag it upward and "learn" the gas as normal
  //    (that caused the one-shot 5000 spikes), but a wrongly-low baseline
  //    can still recover automatically every EMA_MS.
  if (baselineV <= 0.0f) {
    baselineV = v;
    lastBaselineUpdate = millis();
  }
  if (baselineV < BASELINE_FLOOR) baselineV = BASELINE_FLOOR;

  float inst = estimateCo2Ppm(v, baselineV);
  float real = medianCo2(inst);   // spike-proof value shown on the dashboard

  // Slew-limit the published value (rationale: lastPublishedPpm above). One
  // allowed voltage step can be hundreds of ppm at a low baseline — clamped
  // here the dashboard always moves gradually, up or down.
  if (lastPublishedPpm > 0.0f) {
    const float MAX_DPPM = 120.0f;
    if (real > lastPublishedPpm + MAX_DPPM)      real = lastPublishedPpm + MAX_DPPM;
    else if (real < lastPublishedPpm - MAX_DPPM) real = lastPublishedPpm - MAX_DPPM;
  }
  lastPublishedPpm = real;

  float ratio = v / baselineV;
  if (ratio < 1.3f && millis() - lastBaselineUpdate >= EMA_MS) {
    lastBaselineUpdate = millis();
    updateBaseline(v);
    baselineV = (baselineV * 0.9f) + (v * 0.1f);
    if (baselineV < BASELINE_FLOOR) baselineV = BASELINE_FLOOR;
  } else if (ratio >= 1.3f) {
    // Slow upward self-heal (tau = EMA_UP_MS). The old code froze the
    // baseline entirely at ratio >= 1.3, so a cold-start heater ramp
    // (0.046 V -> 0.13 V over ~20 min) pinned it at the cold value and every
    // later reading carried a permanent false elevation. A 5 min tau follows
    // that ramp (self-heals to the true ~400 ppm within minutes after it
    // stops) while moving <10% during a 30-60 s breath peak — real spikes
    // still display almost fully.
    baselineV += (v - baselineV) * (1.0f - exp(-READ_INTERVAL_MS / (float)EMA_UP_MS));
    if (baselineV < BASELINE_FLOOR) baselineV = BASELINE_FLOOR;
  }

  if (real > 0) {
    publishMetric(TOPIC_CO2, real, "ppm");
    if (v < 0.05f) {
      Serial.printf("LOW SIGNAL: Raw %.3f V (bl %.3f) -> real %.0f ppm "
                    "(check module power/heater)\n", v, baselineV, real);
    } else {
      Serial.printf("Raw %.3f V (bl %.3f) | instant %.0f ppm | real %.0f ppm\n",
                    v, baselineV, inst, real);
    }
  } else {
    Serial.println("CO2 read failed");
  }
}