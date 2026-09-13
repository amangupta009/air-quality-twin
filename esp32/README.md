# ESP32 Air Quality Sensor Station

Real-hardware publisher for the Air Quality Digital Twin. Reads an approximate
CO2 level from a low-cost MQ-135 (analog) and publishes it to MQTT in the exact
contract the backend consumes (`sensors/{roomId}/{metric}`).

## Wiring
See `src/main.cpp` header for exact pinout. Essentials:
- MQ-135 module: VCC -> 3.3V / GND -> GND / AO -> GPIO34 / DO unconnected
- Common ground is REQUIRED.
- Power the module at 3.3V (not 5V) so AO stays inside the ESP32 ADC range.

> MQ-135 is an estimate, not a calibrated NDIR CO2 sensor. Fine for a
> room-trend demo; use MH-Z19B + PMS5003 for lab-grade accuracy.

## Build & Flash
1. Install PlatformIO Core (or use Arduino IDE with PubSubClient)
2. Edit `WIFI_SSID`, `WIFI_PASS`, `MQTT_BROKER`, `ROOM_ID` in `src/main.cpp`
3. `pio run -t upload`

## Backend switch-over
Set these in the backend (used by `scripts/start-all.sh`):
```
SENSOR_MODE=hardware
MQTT_BROKER=tcp://localhost:1884
```
The broker must be reachable from the ESP32 over WiFi - start-all.sh listens on
0.0.0.0:1884. Point the firmware's `MQTT_BROKER` at your laptop's LAN IP.

The occupancy topic is NOT published by the station - occupants are entered
manually on the dashboard (a person counter cannot be read from a sensor).