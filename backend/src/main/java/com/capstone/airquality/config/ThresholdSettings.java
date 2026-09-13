package com.capstone.airquality.config;

import org.springframework.stereotype.Service;

/**
 * Mutable runtime thresholds. Starts from application.properties values and
 * can be changed live from the dashboard (PUT /api/settings/thresholds)
 * without restarting the backend.
 */
@Service
public class ThresholdSettings {

    private volatile double co2Ppm;

    public ThresholdSettings(ThresholdProperties initial) {
        this.co2Ppm = initial.co2Ppm();
    }

    public double getCo2Ppm() {
        return co2Ppm;
    }

    public void update(Double newCo2) {
        if (newCo2 != null) {
            this.co2Ppm = newCo2;
        }
    }
}