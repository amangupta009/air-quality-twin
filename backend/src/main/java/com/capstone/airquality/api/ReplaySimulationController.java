package com.capstone.airquality.api;

import com.capstone.airquality.domain.AlertEvent;
import com.capstone.airquality.domain.SensorReadingEntity;
import com.capstone.airquality.domain.VentilationAction;
import com.capstone.airquality.repo.AlertEventRepository;
import com.capstone.airquality.repo.SensorReadingRepository;
import com.capstone.airquality.repo.VentilationActionRepository;
import com.capstone.airquality.twin.RoomState;
import com.capstone.airquality.twin.RoomTwinService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Innovation layer: event replay + scenario simulation.
 *
 * Event replay: /api/replay/{room}?minutes=N returns a timeline of CO2
 * samples PLUS timestamped alert and ventilation events so a UI can animate
 * the whole story in sequence (not a static snapshot).
 *
 * Scenario simulation: /api/simulate/{room} predicts how fast CO2 crosses a
 * given limit under a chosen number of people and ventilation state, using
 * the closed-form model in RoomTwinService.projectMinutesToCo2Threshold.
 */
@RestController
@RequestMapping("/api")
public class ReplaySimulationController {

    private final SensorReadingRepository readingRepo;
    private final AlertEventRepository alertRepo;
    private final VentilationActionRepository actionRepo;
    private final RoomTwinService twinService;

    public ReplaySimulationController(SensorReadingRepository readingRepo,
                                      AlertEventRepository alertRepo,
                                      VentilationActionRepository actionRepo,
                                      RoomTwinService twinService) {
        this.readingRepo = readingRepo;
        this.alertRepo = alertRepo;
        this.actionRepo = actionRepo;
        this.twinService = twinService;
    }

    /** Timeline of CO2 samples + alert/action events for playback. */
    @GetMapping("/replay/{roomId}")
    public ResponseEntity<Map<String, Object>> replay(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "60") long minutes) {
        Instant from = Instant.now().minus(Duration.ofMinutes(minutes));
        Instant to = Instant.now();

        List<Map<String, Object>> samples = new ArrayList<>();
        for (SensorReadingEntity r : readingRepo
                .findByRoomIdAndRecordedAtBetweenOrderByRecordedAtAsc(roomId, from, to)) {
            if (!"co2".equals(r.getMetric())) continue;
            samples.add(Map.of("ts", r.getRecordedAt().toString(), "co2", r.getValue()));
        }

        List<Map<String, Object>> events = new ArrayList<>();
        for (AlertEvent a : alertRepo.findByRoomIdOrderByTriggeredAtDesc(roomId)) {
            events.add(Map.of("type", "alert", "ts", a.getTriggeredAt().toString(),
                    "value", a.getObservedValue(), "detail", "CO2 crossed limit"));
        }
        for (VentilationAction a : actionRepo.findByRoomIdOrderByActedAtDesc(roomId)) {
            events.add(Map.of("type", "ventilation", "ts", a.getActedAt().toString(),
                    "value", "ON".equals(a.getAction()) ? 1 : 0,
                    "detail", a.getAction() + " by " + a.getActor()));
        }

        events.sort((x, y) -> ((String) x.get("ts")).compareTo((String) y.get("ts")));
        return ResponseEntity.ok(Map.of("roomId", roomId, "samples", samples, "events", events));
    }

    /** What-if prediction. */
    public record SimulateRequest(Integer occupants, Boolean ventilationOn, Double targetPpm) {}

    @PostMapping("/simulate/{roomId}")
    public ResponseEntity<Map<String, Object>> simulate(
            @PathVariable String roomId,
            @RequestBody SimulateRequest request) {
        RoomState state = twinService.stateFor(roomId);
        if (state == null || state.getCo2Ppm() == null) {
            return ResponseEntity.notFound().build();
        }
        int occupants = request.occupants() != null ? request.occupants()
                : (state.getOccupants() != null ? state.getOccupants() : 0);
        double ach = Boolean.TRUE.equals(request.ventilationOn()) ? 6.0 : 0.5;
        double target = request.targetPpm() != null ? request.targetPpm()
                : Math.min(420.0 + occupants * 15.0 + 380.0, 2500.0);

        double minutes = twinService.projectMinutesToCo2Threshold(roomId, occupants, ach, target);
        double current = state.getCo2Ppm();
        String type = minutes < 0 ? "never" : (minutes == 0 ? "already" : "in");
        return ResponseEntity.ok(Map.of(
                "roomId", roomId,
                "currentCo2", current,
                "occupants", occupants,
                "ventilationOn", request.ventilationOn(),
                "targetPpm", target,
                "reachLimitType", type,
                "minutesToLimit", minutes
        ));
    }

    /** Alias used by the docs. */
    @GetMapping("/rooms/{roomId}/replay")
    public ResponseEntity<Map<String, Object>> replayAlias(@PathVariable String roomId) {
        return replay(roomId, 60);
    }
}
