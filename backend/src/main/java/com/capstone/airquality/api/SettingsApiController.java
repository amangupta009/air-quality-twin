package com.capstone.airquality.api;

import com.capstone.airquality.config.ThresholdSettings;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Live settings the dashboard can read and change without a restart. */
@RestController
@RequestMapping("/api/settings")
public class SettingsApiController {

    public record ThresholdUpdate(
            @Positive(message = "CO2 limit must be positive") Double co2Ppm) {
    }

    private final ThresholdSettings thresholds;

    public SettingsApiController(ThresholdSettings thresholds) {
        this.thresholds = thresholds;
    }

    @GetMapping("/thresholds")
    public Map<String, Double> get() {
        return Map.of("co2Ppm", thresholds.getCo2Ppm());
    }

    @PutMapping("/thresholds")
    public ResponseEntity<Map<String, Double>> update(@RequestBody ThresholdUpdate update) {
        if (update.co2Ppm() == null) {
            return ResponseEntity.badRequest().build();
        }
        thresholds.update(update.co2Ppm());
        return ResponseEntity.ok(get());
    }
}