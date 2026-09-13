package com.capstone.airquality.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed view of the thresholds.* entries in application.properties.
 * Kept configurable so the evaluation dossier can re-run alert-precision
 * experiments with different limits without touching code.
 */
@ConfigurationProperties(prefix = "thresholds")
public record ThresholdProperties(
        double co2Ppm) {
}