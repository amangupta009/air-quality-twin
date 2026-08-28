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
    private volatile double pm25Ugm3;

    public ThresholdSettings(ThresholdProperties initial) {
        this.co2Ppm = initial.co2Ppm();
        this.pm25Ugm3 = initial.pm25Ugm3();
    }

    public double getCo2Ppm() {
        return co2Ppm;
    }

    public double getPm25Ugm3() {
        return pm25Ugm3;
    }

    public void update(Double newCo2, Double newPm25) {
        if (newCo2 != null) {
            this.co2Ppm = newCo2;
        }
        if (newPm25 != null) {
            this.pm25Ugm3 = newPm25;
        }
    }
}
