package com.capstone.airquality.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed view of the sensor.* entries in application.properties.
 *
 * 'mode' is THE switch for the simulator-to-real-sensor swap:
 *   simulator -> SimulatorFeed bean is created and generates data.
 *   hardware  -> SimulatorFeed bean does not exist; a real device publishes
 *                to MQTT instead. Nothing else in the app changes.
 */
@ConfigurationProperties(prefix = "sensor")
public record SensorProperties(
        String mode,
        String defaultRoom,
        Simulator simulator) {

    public record Simulator(
            long intervalMs,
            double timeFactor,
            int capacity,
            double outdoorCo2Ppm,
            double infiltrationAch,
            double ventilationAch) {
    }
}
