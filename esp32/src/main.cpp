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
 *     VCC  -> ESP32 3.3V   (powering at 3.3V keeps AO within ADC range)
 *     GND  -> ESP32 GND
 *     AO   -> ESP32 GPIO34 (ADC1_CH6)
 *     DO   -> leave unconnected
 *
 * Dependencies (Arduino IDE / PlatformIO):
 *   - PubSubClient by Nick O'Leary
 *
 * Build with PlatformIO (recommended) - see platformio.ini in this folder.
 * Flash: pio run -t upload
 */

#include <WiFi.h>
#include <PubSubClient.h>

// ---------------------------------------------------------------------------
// CONFIGURATION - edit these for your environment
// ---------------------------------------------------------------------------
const char *WIFI_SSID     = "YOUR_WIFI_SSID";
const char *WIFI_PASS     = "YOUR_WIFI_PASSWORD";

const char *MQTT_BROKER   = "YOUR_BACKEND_LAN_IP";  // laptop/backend host LAN IP
const int   MQTT_PORT     = 1884;              // mosquitto dev listener port
const char *ROOM_ID       = "room101";         // must match backend room
const char *TOPIC_CO2     = "sensors/room101/co2";

const int   READ_INTERVAL_MS = 5000;           // publish every 5s

// GPIO wiring
const int MQ135_AO = 34;    // ESP32 GPIO34 (ADC1_CH6) reads MQ-135 analog out

const int WARMUP_MS    = 120000;  // 2min heater settle before trusting readings
const int SAMPLES      = 20;      // averaging samples per read
const int EMA_MS       = 30000;   // slow baseline adapt window for sensor drift

// ---------------------------------------------------------------------------
WiFiClient espClient;
PubSubClient mqtt(espClient);

unsigned long lastRead = 0;
unsigned long bootTime = 0;
unsigned long lastBaselineUpdate = 0;
float baselineV = 0.0f;      // slow-moving baseline (assumed ~400 ppm air)

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
  float ppm = 400.0f * pow(ratio, 3.5f);
  if (ppm > 5000.0f) ppm = 5000.0f;
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

  // Heater warm-up: sensor output is not stable for the first couple minutes.
  if (millis() - bootTime < WARMUP_MS) {
    delay(500);
    return;
  }

  if (millis() - lastRead >= READ_INTERVAL_MS) {
    lastRead = millis();

    float v = readAnalogVoltage();

    // Slow-moving baseline absorbs heater/drift; update it before estimating.
    if (millis() - lastBaselineUpdate >= EMA_MS) {
      lastBaselineUpdate = millis();
      updateBaseline(v);
      baselineV = (baselineV * 0.9f) + (v * 0.1f);
    }

    float co2 = estimateCo2Ppm(v, baselineV);
    if (co2 > 0) {
      publishMetric(TOPIC_CO2, co2, "ppm");
      Serial.printf("Raw %.3f V (bl %.3f) -> CO2 ~%.0f ppm\n", v, baselineV, co2);
    } else {
      Serial.println("CO2 read failed");
    }
  }
}