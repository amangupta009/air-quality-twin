package com.capstone.airquality.twin;

import com.capstone.airquality.config.ThresholdSettings;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The live digital state model (innovation layer, part 1).
 *
 * Holds one RoomState per room in memory and derives human-meaningful
 * status/recommendations from it. This is what makes the system a "twin"
 * rather than a passive dashboard: the state is queryable and projectable
 * at any moment without touching the database.
 */
@Service
public class RoomTwinService {

    public enum Status { OK, WARNING, ALERT }

    private final ThresholdSettings thresholds;
    private final Map<String, RoomState> rooms = new ConcurrentHashMap<>();

    public RoomTwinService(ThresholdSettings thresholds) {
        this.thresholds = thresholds;
    }

    public RoomState stateFor(String roomId) {
        return rooms.computeIfAbsent(roomId, RoomState::new);
    }

    public Collection<RoomState> allStates() {
        return rooms.values();
    }

    public boolean isVentilationOn(String roomId) {
        return stateFor(roomId).isVentilationOn();
    }

    public void applyReading(String roomId, String metric, double value, Instant at) {
        RoomState state = stateFor(roomId);
        switch (metric) {
            case "co2" -> state.setCo2Ppm(value, at);
            case "temperature" -> state.setTemperature(value, at);
            case "humidity" -> state.setHumidity(value, at);
            case "occupancy" -> state.setOccupants((int) Math.round(value), at);
            default -> {
            }
        }
    }

    public void setVentilation(String roomId, boolean on) {
        stateFor(roomId).setVentilationOn(on, Instant.now());
    }

    /**
     * Dynamic thresholds based on occupancy:
     *   0 people  → always OK (no indoor source)
     *   N people  → alertThreshold  = 420 + N*15 + 380  (caps at 2500)
     *               warningThreshold = alert * 0.8
     */
    public Status statusOf(String roomId) {
        RoomState s = stateFor(roomId);

        if (s.getOccupants() == null || s.getOccupants() <= 0) {
            // No occupancy signal: fall back to the fixed configured thresholds so
            // real sensor readings still surface ALERT/WARNING on the dashboard.
            return co2Status(s);
        }

        int occupants = s.getOccupants();
        double alertLimit = Math.min(420.0 + occupants * 15.0 + 380.0, 2500.0);
        double warningLimit = alertLimit * 0.8;
        Status worst = Status.OK;

        if (s.getCo2Ppm() != null) {
            if (s.getCo2Ppm() >= alertLimit) {
                worst = Status.ALERT;
            } else if (s.getCo2Ppm() >= warningLimit) {
                worst = Status.WARNING;
            }
        }

        return worst;
    }

    private Status co2Status(RoomState s) {
        if (s.getCo2Ppm() == null) return Status.OK;
        if (s.getCo2Ppm() >= thresholds.getCo2Ppm()) return Status.ALERT;
        if (s.getCo2Ppm() >= thresholds.getCo2Ppm() * 0.8) return Status.WARNING;
        return Status.OK;
    }

    private Status worse(Status a, Status b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    public String recommendationFor(String roomId) {
        RoomState s = stateFor(roomId);
        Status status = statusOf(roomId);
        if (status == Status.ALERT) {
            return s.isVentilationOn()
                    ? "Alert: keep ventilation ON until levels drop below 80% of the limit."
                    : "Alert: increase ventilation now (open windows / turn ventilation ON).";
        }
        if (status == Status.WARNING) {
            return "Approaching limit - consider increasing ventilation.";
        }
        return "Air quality is within safe limits.";
    }

    public double projectMinutesToCo2Threshold(String roomId, Integer occupantsOverride,
                                               Double achOverride, double targetPpm) {
        RoomState s = stateFor(roomId);
        if (s.getCo2Ppm() == null) {
            return -1;
        }
        int occupants = occupantsOverride != null ? occupantsOverride
                : (s.getOccupants() != null ? s.getOccupants() : 0);
        double ach = achOverride != null ? achOverride : 0.5;
        double k = ach / 3600.0;
        double genPerPerson = 0.045;
        double c0 = s.getCo2Ppm();
        if (c0 >= targetPpm) {
            return 0;
        }
        if (k < 1e-9) {
            return occupants <= 0 ? -1 : ((targetPpm - c0) / (occupants * genPerPerson)) / 60.0;
        }
        double ceq = 420.0 + (occupants * genPerPerson) / k;
        if (targetPpm >= ceq) {
            return -1;
        }
        double seconds = -Math.log((targetPpm - ceq) / (c0 - ceq)) / k;
        return seconds / 60.0;
    }
}
