package com.capstone.airquality.twin;

import com.capstone.airquality.config.ThresholdProperties;
import com.capstone.airquality.config.ThresholdSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the occupancy-aware status model and the scenario
 * (projection) engine - edge cases and robustness without any Spring context.
 */
class TwinEdgeCaseTest {

    private RoomTwinService twin;

    @BeforeEach
    void setUp() {
        twin = new RoomTwinService(new ThresholdSettings(new ThresholdProperties(1000)));
    }

    @Test
    void emptyRoomFallsBackToFixedCo2Threshold() {
        // No occupants -> CO2 checked against the fixed configured threshold (1000)
        // so real sensor readings still surface ALERT on the dashboard.
        twin.applyReading("r", "occupancy", 0, Instant.now());
        twin.applyReading("r", "co2", 2000, Instant.now());
        assertEquals(RoomTwinService.Status.ALERT, twin.statusOf("r"));

        twin.applyReading("r", "co2", 820, Instant.now());
        assertEquals(RoomTwinService.Status.WARNING, twin.statusOf("r"));

        twin.applyReading("r", "co2", 500, Instant.now());
        assertEquals(RoomTwinService.Status.OK, twin.statusOf("r"));
    }

    @Test
    void crowdedRoomEscalatesFaster() {
        // 100 people -> alert limit = min(420+100*15+380, 2500) = 2300
        twin.applyReading("r", "occupancy", 100, Instant.now());
        twin.applyReading("r", "co2", 2000, Instant.now());
        assertEquals(RoomTwinService.Status.WARNING, twin.statusOf("r"));

        twin.applyReading("r", "co2", 2400, Instant.now());
        assertEquals(RoomTwinService.Status.ALERT, twin.statusOf("r"));
    }

    @Test
    void co2AtOrBelowOutdoorStaysOk() {
        twin.applyReading("r", "occupancy", 5, Instant.now());
        twin.applyReading("r", "co2", 410, Instant.now());
        assertEquals(RoomTwinService.Status.OK, twin.statusOf("r"));
    }

    @Test
    void projectionNegativeWithEmptyRoom() {
        twin.applyReading("r", "occupancy", 0, Instant.now());
        twin.applyReading("r", "co2", 450, Instant.now());
        assertEquals(-1, twin.projectMinutesToCo2Threshold("r", 0, 0.5, 1000), 0.001);
    }

    @Test
    void projectionSomewhereInFutureForCrowdedNoVent() {
        twin.applyReading("r", "occupancy", 50, Instant.now());
        twin.applyReading("r", "co2", 450, Instant.now());
        double minutes = twin.projectMinutesToCo2Threshold("r", 50, 0.5, 1550);
        assertTrue(minutes > 0, "should reach limit eventually without ventilation");
    }

    @Test
    void neverProjectedWhenVentilationAdequate() {
        twin.applyReading("r", "occupancy", 19, Instant.now());
        twin.applyReading("r", "co2", 450, Instant.now());
        double minutes = twin.projectMinutesToCo2Threshold("r", 19, 6.0, 1085);
        assertEquals(-1, minutes, 0.001);
    }

    @Test
    void missingOccupancyFallsBackToFixedCo2Threshold() {
        // No occupancy reading -> CO2 checked against configured threshold (1000),
        // so a real sensor spike is not silently ignored.
        twin.applyReading("r", "co2", 3000, Instant.now());
        assertEquals(RoomTwinService.Status.ALERT, twin.statusOf("r"));
    }
}
