package com.capstone.airquality.twin;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit test - no Spring context, no database, no MQTT broker.
 * With occupancy-aware status, every CO2 escalation test must seed occupants.
 */
class RoomTwinServiceTest {

    private final RoomTwinService twin = new RoomTwinService(
            new com.capstone.airquality.config.ThresholdSettings(
                    new com.capstone.airquality.config.ThresholdProperties(1000, 35)));

    private void withPeople(int n) {
        twin.applyReading("roomX", "occupancy", n, Instant.now());
    }

    @Test
    void statusIsOkWhenNothingReportedYet() {
        assertEquals(RoomTwinService.Status.OK, twin.statusOf("roomX"));
    }

    @Test
    void statusEscalatesToWarningThenAlert() {
        withPeople(30); // alert limit = 420 + 30*15 + 380 = 1250
        twin.applyReading("roomX", "co2", 1000, Instant.now());
        assertEquals(RoomTwinService.Status.WARNING, twin.statusOf("roomX"));

        twin.applyReading("roomX", "co2", 1300, Instant.now());
        assertEquals(RoomTwinService.Status.ALERT, twin.statusOf("roomX"));
    }

    @Test
    void recommendationMentionsVentilationDuringAlert() {
        withPeople(30);
        twin.applyReading("roomX", "co2", 1300, Instant.now());
        String advice = twin.recommendationFor("roomX");
        assertTrue(advice.toLowerCase().contains("ventilation"));
    }

    @Test
    void projectionReturnsZeroWhenAlreadyAboveTarget() {
        withPeople(30);
        twin.applyReading("roomX", "co2", 1300, Instant.now());
        assertEquals(0, twin.projectMinutesToCo2Threshold("roomX", null, null, 1000), 0.001);
    }
}
