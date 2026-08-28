# ESP32 Air Quality Sensor Station

Real-hardware publisher for the Air Quality Digital Twin. Reads CO2 (MH-Z19B)
and PM2.5/PM10 (PMS5003) and publishes them to MQTT in the exact contract the
backend consumes (`sensors/{roomId}/{metric}`).

## Wiring
See `src/main.cpp` header for exact pinout. Essentials:
- MH-Z19B: 5V / GND / TX->GPIO16(RX2) / RX->GPIO17(TX2)
- PMS5003: 5V / GND / TX->GPIO5
- Common ground is REQUIRED.

## Build & Flash
1. Install PlatformIO Core (or use Arduino IDE with PubSubClient + ArduinoJson)
2. Edit `WIFI_SSID`, `WIFI_PASS`, `MQTT_BROKER`, `ROOM_ID` in `src/main.cpp`
3. `pio run -t upload`

## Backend switch-over
Set these in the backend:
```
sensor.mode=hardware
mqtt.broker-uri=tcp://<backend-ip>:1883
```
The occupancy topic is NOT published by the station - occupants are entered
manually on the dashboard (a person counter cannot be read from a sensor).
