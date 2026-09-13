package com.capstone.airquality.api;

import com.capstone.airquality.domain.CalibrationProfile;
import com.capstone.airquality.repo.CalibrationProfileRepository;
import com.capstone.airquality.repo.RoomRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Module: calibration screen.
 *
 * Lets an ADMIN set a scale + offset to correct each sensor's raw readings.
 *   calibrated = raw * scaleValue + offsetValue
 * The most recent profile per (room, metric) becomes active and is applied
 * in ReadingIngestService.applyCalibration.
 */
@RestController
@RequestMapping("/api/calibration")
public class CalibrationController {

    public record CalibrationRequest(
            @NotBlank String roomId,
            @NotBlank @Pattern(regexp = "co2", message = "metric must be co2") String metric,
            Double offsetValue,
            Double scaleValue,
            @NotBlank String calibratedBy,
            String notes) {
    }

    private final CalibrationProfileRepository calibrationRepo;
    private final RoomRepository roomRepo;

    public CalibrationController(CalibrationProfileRepository calibrationRepo, RoomRepository roomRepo) {
        this.calibrationRepo = calibrationRepo;
        this.roomRepo = roomRepo;
    }

    /** All calibration profiles for a room (history), or the default room if unspecified. */
    @GetMapping("/{roomId}")
    public List<CalibrationProfile> history(@PathVariable String roomId) {
        return calibrationRepo.findAll().stream()
                .filter(p -> p.getRoomId().equals(roomId))
                .toList();
    }

    /** Set or update the correction for a sensor. */
    @PostMapping
    public ResponseEntity<CalibrationProfile> calibrate(@Valid @RequestBody CalibrationRequest request) {
        if (!roomRepo.existsById(request.roomId())) {
            return ResponseEntity.notFound().build();
        }
        CalibrationProfile profile = new CalibrationProfile();
        profile.setRoomId(request.roomId());
        profile.setMetric(request.metric());
        profile.setOffsetValue(request.offsetValue() != null ? request.offsetValue() : 0.0);
        profile.setScaleValue(request.scaleValue() != null ? request.scaleValue() : 1.0);
        profile.setCalibratedBy(request.calibratedBy());
        profile.setCalibratedAt(Instant.now());
        profile.setNotes(request.notes());
        return ResponseEntity.ok(calibrationRepo.save(profile));
    }
}
