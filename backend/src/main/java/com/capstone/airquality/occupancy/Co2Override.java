package com.capstone.airquality.occupancy;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manual CO2 override. When the operator enters a CO2 value directly,
 * that value is used for the next simulator tick instead of the physics model.
 * The override is consumed once and then cleared so the physics model resumes.
 */
@Component
public class Co2Override {

    private final Map<String, Double> overrides = new ConcurrentHashMap<>();

    public void set(String roomId, double co2Ppm) {
        overrides.put(roomId, co2Ppm);
    }

    /** Returns the override value and removes it (one-shot), or null if none. */
    public Double consume(String roomId) {
        return overrides.remove(roomId);
    }

    /** Peek without consuming. Returns null if no override is pending. */
    public Double peek(String roomId) {
        return overrides.get(roomId);
    }

    public void clear(String roomId) {
        overrides.remove(roomId);
    }
}
