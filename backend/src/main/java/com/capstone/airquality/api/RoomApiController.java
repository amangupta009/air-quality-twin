package com.capstone.airquality.api;

import com.capstone.airquality.domain.AlertEvent;
import com.capstone.airquality.domain.Room;
import com.capstone.airquality.domain.SensorReadingEntity;
import com.capstone.airquality.domain.VentilationAction;
import com.capstone.airquality.ingest.ReadingIngestService;
import com.capstone.airquality.occupancy.Co2Override;
import com.capstone.airquality.occupancy.OccupancyControl;
import com.capstone.airquality.repo.AlertEventRepository;
import com.capstone.airquality.repo.RoomRepository;
import com.capstone.airquality.repo.SensorReadingRepository;
import com.capstone.airquality.repo.VentilationActionRepository;
import com.capstone.airquality.sensor.SensorReading;
import com.capstone.airquality.twin.RoomSnapshotDto;
import com.capstone.airquality.twin.RoomTwinService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Read/write API consumed by the React dashboard.
 * The frontend NEVER talks to PostgreSQL directly - this controller is the
 * only door into the system for user actions and dashboard reads.
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomApiController {

    public record VentilationRequest(
            @NotBlank
            @Pattern(regexp = "ON|OFF", message = "action must be ON or OFF")
            String action,
            @NotBlank
            String actor,
            String note) {
    }

    public record RenameRequest(
            @NotBlank(message = "room name cannot be empty")
            String name) {
    }

    public record OccupancyRequest(
            @Min(0)
            @Max(500)
            int occupants) {
    }

    public record Co2Request(
            @Min(300)
            @Max(5000)
            double co2Ppm) {
    }

    public record EnterRequest(
            @NotBlank
            String actor) {
    }

    public record CreateRoomRequest(
            @NotBlank
            String id,
            @NotBlank
            String name,
            Integer capacity,
            Double volumeM3) {
    }

    private final RoomRepository roomRepo;
    private final VentilationActionRepository actionRepo;
    private final AlertEventRepository alertRepo;
    private final SensorReadingRepository readingRepo;
    private final RoomTwinService twinService;
    private final OccupancyControl occupancyControl;
    private final Co2Override co2Override;
    private final ReadingIngestService readingIngestService;

    public RoomApiController(RoomRepository roomRepo,
                             VentilationActionRepository actionRepo,
                             AlertEventRepository alertRepo,
                             SensorReadingRepository readingRepo,
                             RoomTwinService twinService,
                             OccupancyControl occupancyControl,
                             Co2Override co2Override,
                             ReadingIngestService readingIngestService) {
        this.roomRepo = roomRepo;
        this.actionRepo = actionRepo;
        this.alertRepo = alertRepo;
        this.readingRepo = readingRepo;
        this.twinService = twinService;
        this.occupancyControl = occupancyControl;
        this.co2Override = co2Override;
        this.readingIngestService = readingIngestService;
    }

    /** Live status of every known room (from the digital twin, not the DB). */
    @GetMapping
    public List<RoomSnapshotDto> allRooms() {
        return roomRepo.findAll().stream()
                .map(this::toSnapshot)
                .toList();
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomSnapshotDto> oneRoom(@PathVariable String roomId) {
        return roomRepo.findById(roomId)
                .map(room -> ResponseEntity.ok(toSnapshot(room)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Module 5: the audit trail endpoint. Dashboard button -> POST here ->
     * logged with who/when -> twin updated so the simulator/sensors see the
     * ventilation change.
     */
    @PostMapping("/{roomId}/ventilation")
    public ResponseEntity<VentilationAction> setVentilation(
            @PathVariable String roomId,
            @Valid @RequestBody VentilationRequest request) {

        if (!roomRepo.existsById(roomId)) {
            return ResponseEntity.notFound().build();
        }

        VentilationAction action = new VentilationAction();
        action.setRoomId(roomId);
        action.setAction(request.action());
        action.setActor(request.actor());
        action.setNote(request.note());
        action.setActedAt(Instant.now());
        VentilationAction saved = actionRepo.save(action);

        twinService.setVentilation(roomId, "ON".equals(request.action()));
        return ResponseEntity
                .created(URI.create("/api/rooms/" + roomId + "/ventilation/" + saved.getId()))
                .body(saved);
    }

    /**
     * Create a brand-new room. ADMIN only (enforced in AuthInterceptor).
     * id is derived from the entered name ("name is id"; spaces become dashes).
     */
    @PostMapping
    public ResponseEntity<Room> create(@Valid @RequestBody CreateRoomRequest request) {
        String id = sanitizeRoomId(request.id());
        if (id.isEmpty() || roomRepo.existsById(id)) {
            return ResponseEntity.badRequest().build();
        }
        String name = request.name() != null && !request.name().isBlank() ? request.name().trim() : id;
        int cap = request.capacity() != null && request.capacity() > 0 ? request.capacity() : 8;
        double vol = request.volumeM3() != null && request.volumeM3() > 0 ? request.volumeM3() : 60.0;
        Room room = new Room(id, name, cap, vol);
        roomRepo.save(room);
        twinService.stateFor(id);
        return ResponseEntity.ok(room);
    }

    /**
     * Room entry audit: who entered which room and when.
     * The room must already exist - creating a new one is a separate, ADMIN-only
     * operation (POST /api/rooms).
     */
    @PostMapping("/{roomId}/enter")
    public ResponseEntity<VentilationAction> enter(
            @PathVariable String roomId,
            @Valid @RequestBody EnterRequest request) {

        String id = sanitizeRoomId(roomId);
        if (id.isEmpty() || !roomRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        VentilationAction audit = new VentilationAction();
        audit.setRoomId(id);
        audit.setAction("ENTER");
        audit.setActor(request.actor());
        audit.setNote("entered room");
        audit.setActedAt(Instant.now());
        VentilationAction saved = actionRepo.save(audit);

        return ResponseEntity
                .created(URI.create("/api/rooms/" + id + "/enter/" + saved.getId()))
                .body(saved);
    }

    /** Normalizes a free-text room name into the id used across topics/DB. */
    private String sanitizeRoomId(String raw) {
        return raw.trim().toLowerCase()
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-z0-9-]", "");
    }

    /** Rename a room (manual name entry from the dashboard). */
    @PatchMapping("/{roomId}/name")
    public ResponseEntity<Room> rename(@PathVariable String roomId,
                                       @Valid @RequestBody RenameRequest request) {
        return roomRepo.findById(roomId)
                .map(room -> {
                    room.setName(request.name());
                    return ResponseEntity.ok(roomRepo.save(room));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Operator sets how many people are actually in the room right now.
     * The value is persisted (occupancy_snapshots), shown on the twin, and
     * pinned so CO2 rises proportionally to the entered head-count.
     */
    @PostMapping("/{roomId}/occupancy")
    public ResponseEntity<Map<String, Object>> setOccupancy(
            @PathVariable String roomId,
            @Valid @RequestBody OccupancyRequest request) {

        if (!roomRepo.existsById(roomId)) {
            return ResponseEntity.notFound().build();
        }
        occupancyControl.set(roomId, request.occupants());
        // Feed it through the normal MQTT-free path: twin + snapshot + broadcast.
        readingIngestService.onReading(new SensorReading(
                roomId, "occupancy", request.occupants(), "persons", Instant.now()));
        return ResponseEntity.ok(Map.of("roomId", roomId, "occupants", request.occupants()));
    }

    /**
     * Manual CO2 override: operator types a ppm value and the simulator
     * uses it for the next tick (one-shot), then resumes physics.
     */
    @PostMapping("/{roomId}/co2")
    public ResponseEntity<Map<String, Object>> setCo2(
            @PathVariable String roomId,
            @Valid @RequestBody Co2Request request) {

        if (!roomRepo.existsById(roomId)) {
            return ResponseEntity.notFound().build();
        }
        co2Override.set(roomId, request.co2Ppm());
        // Also push the value through the twin immediately so the UI shows it.
        twinService.applyReading(roomId, "co2", request.co2Ppm(), Instant.now());
        readingIngestService.onReading(new SensorReading(
                roomId, "co2", request.co2Ppm(), "ppm", Instant.now()));
        return ResponseEntity.ok(Map.of("roomId", roomId, "co2Ppm", request.co2Ppm()));
    }

    /** Audit view: who did what, when. */
    @GetMapping("/{roomId}/actions")
    public List<VentilationAction> actions(@PathVariable String roomId) {
        return actionRepo.findByRoomIdOrderByActedAtDesc(roomId);
    }

    /** Alert history: every threshold crossing and when it was resolved. */
    @GetMapping("/{roomId}/alerts")
    public List<AlertEvent> alerts(@PathVariable String roomId) {
        return alertRepo.findByRoomIdOrderByTriggeredAtDesc(roomId);
    }

    /** Persisted readings for charts/replay, e.g. /readings?minutes=30 */
    @GetMapping("/{roomId}/readings")
    public List<SensorReadingEntity> readings(@PathVariable String roomId,
                                              @RequestParam(defaultValue = "30") long minutes) {
        Instant to = Instant.now();
        return readingRepo.findByRoomIdAndRecordedAtBetweenOrderByRecordedAtAsc(
                roomId, to.minusSeconds(minutes * 60), to);
    }

    private RoomSnapshotDto toSnapshot(Room room) {
        var state = twinService.stateFor(room.getId());
        return new RoomSnapshotDto(
                room.getId(),
                room.getName(),
                state.getCo2Ppm(),
                state.getTemperature(),
                state.getHumidity(),
                state.getOccupants(),
                state.isVentilationOn(),
                twinService.statusOf(room.getId()).toString(),
                twinService.recommendationFor(room.getId()),
                state.getLastUpdate());
    }
}
