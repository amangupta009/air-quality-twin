#!/usr/bin/env python3
"""
Standalone air-quality sensor simulator.

Alternative to the backend's built-in Java simulator (sensor.mode=simulator).
Use this when you want the backend in 'hardware' mode but still need fake
data - e.g. load testing, or running the simulator on a different machine,
or demonstrating that ingestion works from an EXTERNAL publisher.

Publishes to the exact topic contract the future ESP32 will use:
    sensors/{roomId}/co2        {"value": <ppm>,   "unit": "ppm",   "ts": iso8601}
    sensors/{roomId}/occupancy  {"value": <count>, "unit": "persons", "ts": iso8601}

Physics model matches SimulatorFeed.java so behaviour is comparable:
    dCO2/dt = occupants * GEN - ACH/3600 * (CO2 - outdoor)

Usage:
    pip install -r requirements.txt
    python3 simulator.py [--room room101] [--interval 5] [--speed 12]
"""

import argparse
import json
import random
import socket
import time
import uuid
from datetime import datetime, timezone

import paho.mqtt.client as mqtt

GEN_PPM_PER_PERSON_PER_SEC = 0.045
OUTDOOR_CO2 = 420.0


class SimulatorState:
    def __init__(self, capacity: int):
        self.capacity = capacity
        self.co2 = OUTDOOR_CO2 + 20.0
        self.occupants = 0
        self.rng = random.Random()

    def tick(self, dt: float, ventilation_on: bool) -> dict:
        self._update_occupants()
        ach = 6.0 if ventilation_on else 0.5
        decay = ach / 3600.0
        self.co2 += (self.occupants * GEN_PPM_PER_PERSON_PER_SEC - decay * (self.co2 - OUTDOOR_CO2)) * dt
        self.co2 += self.rng.gauss(0, 2.0)
        self.co2 = max(OUTDOOR_CO2 - 10, self.co2)

        return {"co2": round(self.co2, 1), "occupancy": self.occupants}

    def _update_occupants(self):
        hour = time.localtime().tm_hour
        work_hours = 8 <= hour < 18
        target = round(self.capacity * 0.75) if work_hours else 0
        if self.occupants < target and self.rng.random() < 0.5:
            self.occupants += 1
        elif self.occupants > target and self.rng.random() < 0.3:
            self.occupants -= 1
        if self.rng.random() < 0.05:
            self.occupants = max(0, min(self.capacity, self.occupants + self.rng.choice([-1, 1])))


def main() -> None:
    parser = argparse.ArgumentParser(description="MQTT air-quality simulator")
    parser.add_argument("--room", default="room101")
    parser.add_argument("--broker", default="localhost")
    parser.add_argument("--port", type=int, default=1883)
    parser.add_argument("--interval", type=float, default=5.0, help="seconds between publishes")
    parser.add_argument("--speed", type=float, default=12.0,
                        help="model-time acceleration factor")
    args = parser.parse_args()

    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2,
                         client_id=f"sim-{socket.gethostname()}-{uuid.uuid4().hex[:6]}")
    client.connect(args.broker, args.port)
    client.loop_start()

    state = SimulatorState(capacity=8)
    dt = args.interval * args.speed
    print(f"Publishing to sensors/{args.room}/{{co2,occupancy}} every "
          f"{args.interval}s (model step {dt:.0f}s). Ctrl+C to stop.")

    try:
        while True:
            readings = state.tick(dt, ventilation_on=False)  # set True to demo vent effect
            ts = datetime.now(timezone.utc).isoformat()
            units = {"co2": "ppm", "occupancy": "persons"}
            for metric, value in readings.items():
                payload = json.dumps({"value": value, "unit": units[metric], "ts": ts})
                client.publish(f"sensors/{args.room}/{metric}", payload)
                print(f"  {metric:>9} = {value}")
            time.sleep(args.interval)
    except KeyboardInterrupt:
        print("\nStopped.")
    finally:
        client.loop_stop()
        client.disconnect()


if __name__ == "__main__":
    main()
