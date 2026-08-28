package com.capstone.airquality.sensor;

/**
 * Callback the data source calls for every reading it receives.
 * The ingest pipeline implements this; it never knows whether the reading
 * came from the simulator or a real device.
 */
@FunctionalInterface
public interface SensorReadingListener {

    void onReading(SensorReading reading);
}
