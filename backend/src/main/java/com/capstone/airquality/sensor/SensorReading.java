package com.capstone.airquality.sensor;

import java.time.Instant;

/**
 * One immutable measurement travelling through the pipeline.
 * A Java 'record': a compact class whose fields are final and auto-exposed
 * via accessor methods like reading.value() - no getters/setters needed.
 */
public record SensorReading(
        String roomId,
        String metric,
        double value,
        String unit,
        Instant recordedAt) {
}
