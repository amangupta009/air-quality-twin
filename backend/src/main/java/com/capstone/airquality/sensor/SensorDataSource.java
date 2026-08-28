package com.capstone.airquality.sensor;

/**
 * THE abstraction layer required by the architecture:
 * everything downstream depends on this interface, never on "the simulator"
 * or "a specific device".
 *
 * Production implementation : MqttSensorDataSource (listens to MQTT topics).
 * Test implementation       : FakeSensorDataSource (pushes readings directly,
 *                             no broker needed -> fast unit tests).
 */
public interface SensorDataSource {

    void start(SensorReadingListener listener);

    void stop();
}
