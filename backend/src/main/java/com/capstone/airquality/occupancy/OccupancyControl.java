package com.capstone.airquality.occupancy;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manual occupancy overrides. When the operator enters "how many people are
 * in the room", that value PINNS the occupancy for the room: the simulator
 * stops its automatic schedule and uses exactly this number, so CO2 rises
 * proportionally to the entered head-count.
 */
@Component
public class OccupancyControl {

    private final Map<String, Integer> manual = new ConcurrentHashMap<>();

    public void set(String roomId, int occupants) {
        manual.put(roomId, occupants);
    }

    /** Returns null when no manual override exists (simulator runs its own schedule). */
    public Integer get(String roomId) {
        return manual.get(roomId);
    }
}
