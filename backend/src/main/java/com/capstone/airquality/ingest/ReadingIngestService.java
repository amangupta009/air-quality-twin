package com.capstone.airquality.ingest;

import com.capstone.airquality.config.ThresholdSettings;
import com.capstone.airquality.domain.AlertEvent;
import com.capstone.airquality.domain.OccupancySnapshot;
import com.capstone.airquality.domain.SensorReadingEntity;
import com.capstone.airquality.occupancy.OccupancyControl;
import com.capstone.airquality.domain.Room;
import com.capstone.airquality.repo.AlertEventRepository;
import com.capstone.airquality.repo.CalibrationProfileRepository;
import com.capstone.airquality.repo.OccupancySnapshotRepository;
import com.capstone.airquality.repo.RoomRepository;
import com.capstone.airquality.repo.SensorReadingRepository;
import com.capstone.airquality.sensor.SensorReading;
import com.capstone.airquality.sensor.SensorReadingListener;
import com.capstone.airquality.twin.RoomSnapshotDto;
import com.capstone.airquality.twin.RoomTwinService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The single gatekeeper every reading passes through (see data-flow diagram):
 *
 *   MQTT message -> calibrate -> digital twin update -> threshold check
 *                -> persist to PostgreSQL -> WebSocket push to dashboard
 *
 * This is the implementation of SensorReadingListener, so it does not know
 * or care whether readings originate from the simulator or real hardware.
 */
@Service
public class ReadingIngestService implements SensorReadingListener {

    private static final Logger log = LoggerFactory.getLogger(ReadingIngestService.class);

    private final SensorReadingRepository readingRepo;
    private final OccupancySnapshotRepository occupancyRepo;
    private final AlertEventRepository alertRepo;
    private final CalibrationProfileRepository calibrationRepo;
    private final RoomRepository roomRepo;
    private final RoomTwinService twinService;
    private final OccupancyControl occupancyControl;
    private final ThresholdSettings thresholds;
    private final SimpMessagingTemplate websocket;

    /** roomId+metric -> id of the currently open (unresolved) alert, if any. */
    private final Map<String, Long> openAlerts = new ConcurrentHashMap<>();

    public ReadingIngestService(SensorReadingRepository readingRepo,
                                OccupancySnapshotRepository occupancyRepo,
                                AlertEventRepository alertRepo,
                                CalibrationProfileRepository calibrationRepo,
                                RoomRepository roomRepo,
                                RoomTwinService twinService,
                                OccupancyControl occupancyControl,
                                ThresholdSettings thresholds,
                                SimpMessagingTemplate websocket) {
        this.readingRepo = readingRepo;
        this.occupancyRepo = occupancyRepo;
        this.alertRepo = alertRepo;
        this.calibrationRepo = calibrationRepo;
        this.roomRepo = roomRepo;
        this.twinService = twinService;
        this.occupancyControl = occupancyControl;
        this.thresholds = thresholds;
        this.websocket = websocket;
    }

    @Override
    public void onReading(SensorReading reading) {
        double calibrated = applyCalibration(reading);
        Instant at = reading.recordedAt();

        if ("occupancy".equals(reading.metric())) {
            handleOccupancy(reading, calibrated, at);
        } else {
            handleMeasurement(reading, calibrated, at);
            // Single physical sensor setup: mirror the live CO2 measurement to
            // every registered room so any room you enter shows a live reading.
            if ("co2".equals(reading.metric())) {
                for (Room room : roomRepo.findAll()) {
                    if (room.getId().equals(reading.roomId())) {
                        continue;
                    }
                    handleMeasurement(
                            new SensorReading(room.getId(), "co2", reading.value(), reading.unit(), at),
                            calibrated,
                            at);
                    broadcastTwinState(room.getId());
                }
            }
        }

        broadcastTwinState(reading.roomId());
    }

    /** Module 7 in action: corrected = raw * scale + offset of latest profile. */
    private double applyCalibration(SensorReading reading) {
        return calibrationRepo
                .findTopByRoomIdAndMetricOrderByCalibratedAtDesc(reading.roomId(), reading.metric())
                .map(profile -> profile.getScaleValue() * reading.value() + profile.getOffsetValue())
                .orElse(reading.value());
    }

    private void handleOccupancy(SensorReading reading, double value, Instant at) {
        int occupants = (int) Math.round(value);
        OccupancySnapshot snapshot = new OccupancySnapshot();
        snapshot.setRoomId(reading.roomId());
        snapshot.setOccupants(occupants);
        snapshot.setRecordedAt(at);
        occupancyRepo.save(snapshot);
        twinService.applyReading(reading.roomId(), "occupancy", value, at);
        occupancyControl.set(reading.roomId(), occupants); // pin manual head-count
    }

    private void handleMeasurement(SensorReading reading, double value, Instant at) {
        SensorReadingEntity entity = new SensorReadingEntity();
        entity.setRoomId(reading.roomId());
        entity.setMetric(reading.metric());
        entity.setValue(value);
        entity.setUnit(reading.unit());
        entity.setRecordedAt(at);
        readingRepo.save(entity);

        twinService.applyReading(reading.roomId(), reading.metric(), value, at);
        evaluateThreshold(reading.roomId(), reading.metric(), value, at);
    }

    /** Module 4: rising edge opens an alert, falling edge (with hysteresis) closes it. */
    private void evaluateThreshold(String roomId, String metric, double value, Instant at) {
        Double limit = switch (metric) {
            case "co2" -> thresholds.getCo2Ppm();
            default -> null; // no limit configured for other metrics
        };
        if (limit == null) {
            return;
        }
        String key = roomId + "/" + metric;
        Long openAlertId = openAlerts.get(key);
        if (openAlertId == null && value >= limit) {
            AlertEvent alert = new AlertEvent();
            alert.setRoomId(roomId);
            alert.setMetric(metric);
            alert.setThresholdValue(limit);
            alert.setObservedValue(value);
            alert.setTriggeredAt(at);
            AlertEvent saved = alertRepo.save(alert);
            openAlerts.put(key, saved.getId());
            // Operational evidence: alerts are visible in logs too.
            log.warn("ALERT {} {}={} crossed limit {} at {}", roomId, metric, value, limit, at);
        } else if (openAlertId != null && value < limit * 0.9) {
            alertRepo.findById(openAlertId).ifPresent(alert -> {
                alert.setResolvedAt(at);
                alertRepo.save(alert);
            });
            openAlerts.remove(key);
            log.info("ALERT resolved {} {}={} back under {}", roomId, metric, value, limit * 0.9);
        }
    }

    /** Live push (WebSocket arrow in the diagram). */
    private void broadcastTwinState(String roomId) {
        var state = twinService.stateFor(roomId);
        websocket.convertAndSend("/topic/rooms/" + roomId, new RoomSnapshotDto(
                roomId,
                null,
                state.getCo2Ppm(),
                state.getOccupants(),
                state.isVentilationOn(),
                twinService.statusOf(roomId).toString(),
                twinService.recommendationFor(roomId),
                state.getLastUpdate()));
    }
}
