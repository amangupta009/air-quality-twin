/*
 * ESP32 Air Quality Sensor Station
 * --------------------------------
 * Reads real CO2 (MH-Z19B) and PM2.5/PM10 (PMS5003) and publishes them
 * to the same MQTT contract the backend expects:
 *
 *   sensors/{roomId}/co2     {"value": 512.4, "unit": "ppm",   "ts": iso8601}
 *   sensors/{roomId}/pm25    {"value": 8.3,    "unit": "ugm3", "ts": iso8601}
 *
 * Swap the backend to real hardware by setting sensor.mode=hardware and
 * pointing mqtt.broker-uri at this device's broker.
 *
 * Wiring (breadboard):
 *   MH-Z19B:
 *     VCC  -> 5V  (NOT 3.3V - sensor needs 5V)
 *     GND  -> GND (common ground with ESP32)
 *     TX   -> ESP32 RX2 (GPIO16)
 *     RX   -> ESP32 TX2 (GPIO17)  [via 1k resistor for level safety]
 *   PMS5003:
 *     VCC  -> 5V
 *     GND  -> GND
 *     TX   -> ESP32 RX1 (GPIO14)  [or use SoftwareSerial]
 *     (optional) SET -> GND for sleep control
 *
 * Dependencies (Arduino IDE / PlatformIO):
 *   - PubSubClient by Nick O'Leary
 *   - SoftwareSerial or the built-in HW Serial
 *
 * Build with PlatformIO (recommended) - see platformio.ini in this folder.
 * Flash: pio run -t upload
 */

#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>

// ---------------------------------------------------------------------------
// CONFIGURATION - edit these for your environment
// ---------------------------------------------------------------------------
const char *WIFI_SSID     = "YOUR_WIFI";
const char *WIFI_PASS     = "YOUR_WIFI_PASSWORD";

const char *MQTT_BROKER   = "192.168.1.100";   // laptop/backend host IP
const int   MQTT_PORT     = 1883;
const char *ROOM_ID       = "room101";         // must match backend room
const char *TOPIC_CO2     = "sensors/room101/co2";
const char *TOPIC_PM25    = "sensors/room101/pm25";

const int   READ_INTERVAL_MS = 5000;           // publish every 5s

// GPIO wiring
const int MHZ19_RX = 16;   // ESP32 RX2 (read MHZ19 TX)
const int MHZ19_TX = 17;   // ESP32 TX2 (write MHZ19 RX)
const int PMS_RX   = 5;    // ESP32 GPIO5 (SoftwareSerial RX reads PMS TX)
const int PMS_TX   = 18;   // ESP32 GPIO18 (SoftwareSerial TX - not used)

// ---------------------------------------------------------------------------
WiFiClient espClient;
PubSubClient mqtt(espClient);
HardwareSerial mhzSerial(2);   // UART2

// Simple software serial for PMS5003
#include <SoftwareSerial.h>
SoftwareSerial pmsSerial(PMS_RX, PMS_TX);

unsigned long lastRead = 0;

// ---------------------------------------------------------------------------
float readCo2() {
  // MH-Z19B: send 0xFF 0x01 0x86 0x00 0x00 0x00 0x00 0x00 0x79, read 9 bytes
  static const uint8_t cmd[9] = {0xFF, 0x01, 0x86, 0x00, 0x00, 0x00, 0x00, 0x00, 0x79};
  mhzSerial.write(cmd, 9);
  mhzSerial.flush();
  uint8_t buf[9];
  int i = 0;
  unsigned long start = millis();
  while (i < 9 && millis() - start < 200) {
    if (mhzSerial.available()) {
      buf[i++] = mhzSerial.read();
    }
  }
  // Check for 0xFF start and valid checksum
  if (i < 9 || buf[0] != 0xFF) return -1;
  uint8_t sum = 0;
  for (int j = 1; j < 8; j++) sum += buf[j];
  if ((0xFF - sum + 1) != buf[8]) return -1;   // bad checksum
  return (buf[2] * 256) + buf[3];              // CO2 in ppm
}

// ---------------------------------------------------------------------------
bool readPm25(float &pm25, float &pm10) {
  // PMS5003 outputs 32-byte frames (0x42 0x4d ...) at 1Hz
  // We look for the start marker then parse the standard frame.
  uint8_t buf[32];
  int i = 0;
  unsigned long start = millis();
  // Wait for start marker 0x42 0x4D
  while (i < 2 && millis() - start < 1200) {
    while (i < 2 && pmsSerial.available()) {
      uint8_t b = pmsSerial.read();
      if (i == 0 && b != 0x42) continue;
      if (i == 1 && b != 0x4D) { i = 0; continue; }
      buf[i++] = b;
    }
  }
  if (i < 2) return false;
  while (i < 32 && millis() - start < 1200) {
    if (pmsSerial.available()) buf[i++] = pmsSerial.read();
  }
  if (i < 32) return false;
  // Frame length check would go here (buf[2..3] == 0x001C)
  pm25 = (buf[6] * 256) + buf[7];    // PM2.5 (ug/m3, CF=1)
  pm10 = (buf[8] * 256) + buf[9];    // PM10
  return true;
}

// ---------------------------------------------------------------------------
void publishMetric(const char *topic, float value, const char *unit) {
  StaticJsonDocument<128> doc;
  doc["value"] = value;
  doc["unit"]  = unit;
  doc["ts"]    = "?ts?";   // replaced below with ISO time
  // Build ISO-8601 timestamp manually (crude but adequate)
  char ts[32];
  struct tm t;
  if (!getLocalTime(&t)) {
    snprintf(ts, sizeof(ts), "%l", (long)millis());
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

void setup() {
  Serial.begin(115200);
  mhzSerial.begin(9600);       // MH-Z19B at 9600 baud
  pmsSerial.begin(9600);       // PMS5003 at 9600 baud

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
}

void loop() {
  if (!mqtt.connected()) connectMQTT();
  mqtt.loop();

  if (millis() - lastRead >= READ_INTERVAL_MS) {
    lastRead = millis();

    float co2 = readCo2();
    if (co2 > 0) {
      publishMetric(TOPIC_CO2, co2, "ppm");
      Serial.printf("CO2: %.0f ppm\n", co2);
    } else {
      Serial.println("CO2 read failed");
    }

    float pm25, pm10;
    if (readPm25(pm25, pm10)) {
      publishMetric(TOPIC_PM25, pm25, "ugm3");
      Serial.printf("PM2.5: %.1f ug/m3, PM10: %.1f\n", pm25, pm10);
    }
  }
}
