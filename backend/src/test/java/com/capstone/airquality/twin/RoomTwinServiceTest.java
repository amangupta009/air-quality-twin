package com.capstone.airquality.twin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure unit test - no Spring context, no database, no MQTT broker.
 * Runs in milliseconds; this is the pattern we will scale up during the
 * integration/testing phase of the capstone.
 */
class RoomTwinServiceTest {

    private final RoomTwinService twin = new RoomTwinService(
            new com.capstone.airquality.config.ThresholdSettings(
                    new com.capstone.airquality.config.ThresholdProperties(1000, 35)));

    @Test
    void statusIsOkWhenNothingReportedYet() {
        assertEquals(RoomTwinService.Status.OK, twin.statusOf("roomX"));
    }

    @Test
    void statusEscalatesToWarningThenAlert() {
        twin.applyReading("roomX", "co2", 850, java.time.Instant.now());
        assertEquals(RoomTwinService.Status.WARNING, twin.statusOf("roomX"));

        twin.applyReading("roomX", "co2", 1100, java.time.Instant.now());
        assertEquals(RoomTwinService.Status.ALERT, twin.statusOf("roomX"));
    }

    @Test
    void recommendationMentionsVentilationDuringAlert() {
        twin.applyReading("roomY", "co2", 1200, java.time.Instant.now());
        String advice = twin.recommendationFor("roomY");
        org.junit.jupiter.api.Assertions.assertTrue(advice.toLowerCase().contains("ventilation"));
    }

    @Test
    void projectionReturnsZeroWhenAlreadyAboveTarget() {
        twin.applyReading("roomZ", "co2", 1200, java.time.Instant.now());
        assertEquals(0, twin.projectMinutesToCo2Threshold("roomZ", null, null, 1000), 0.001);
    }
}
