package com.capstone.airquality.twin;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Mutable live state of ONE room - the actual "digital twin" cell.
 * Not raw history (that lives in PostgreSQL); this is the current best
 * guess of what the room looks like RIGHT NOW, updated on every reading.
 */
public class RoomState {

    private final String roomId;

    private Double co2Ppm;
    private Double temperature;
    private Double humidity;
    private Integer occupants;
    private boolean ventilationOn;

    private Instant co2UpdatedAt;
    private Instant temperatureUpdatedAt;
    private Instant humidityUpdatedAt;
    private Instant occupancyUpdatedAt;
    private Instant ventilationChangedAt;

    /** metric -> most recent alert state, used for rising/falling edge detection. */
    private final Map<String, Boolean> activeAlerts = new HashMap<>();

    public RoomState(String roomId) {
        this.roomId = roomId;
    }

    public String getRoomId() {
        return roomId;
    }

    public Double getCo2Ppm() {
        return co2Ppm;
    }

    public void setCo2Ppm(Double co2Ppm, Instant at) {
        this.co2Ppm = co2Ppm;
        this.co2UpdatedAt = at;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature, Instant at) {
        this.temperature = temperature;
        this.temperatureUpdatedAt = at;
    }

    public Double getHumidity() {
        return humidity;
    }

    public void setHumidity(Double humidity, Instant at) {
        this.humidity = humidity;
        this.humidityUpdatedAt = at;
    }

    public Integer getOccupants() {
        return occupants;
    }

    public void setOccupants(Integer occupants, Instant at) {
        this.occupants = occupants;
        this.occupancyUpdatedAt = at;
    }

    public boolean isVentilationOn() {
        return ventilationOn;
    }

    public void setVentilationOn(boolean ventilationOn, Instant at) {
        this.ventilationOn = ventilationOn;
        this.ventilationChangedAt = at;
    }

    public Instant getLastUpdate() {
        Instant latest = co2UpdatedAt;
        if (temperatureUpdatedAt != null && (latest == null || temperatureUpdatedAt.isAfter(latest))) {
            latest = temperatureUpdatedAt;
        }
        if (humidityUpdatedAt != null && (latest == null || humidityUpdatedAt.isAfter(latest))) {
            latest = humidityUpdatedAt;
        }
        if (occupancyUpdatedAt != null && (latest == null || occupancyUpdatedAt.isAfter(latest))) {
            latest = occupancyUpdatedAt;
        }
        return latest;
    }

    public Map<String, Boolean> getActiveAlerts() {
        return activeAlerts;
    }
}
