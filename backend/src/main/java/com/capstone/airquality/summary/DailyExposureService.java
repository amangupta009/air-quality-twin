package com.capstone.airquality.summary;

import com.capstone.airquality.config.ThresholdSettings;
import com.capstone.airquality.domain.DailyExposureSummary;
import com.capstone.airquality.domain.SensorReadingEntity;
import com.capstone.airquality.repo.DailyExposureSummaryRepository;
import com.capstone.airquality.repo.SensorReadingRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Computes and serves the Daily Exposure Summary (Module 6).
 *
 * For a given UTC day it aggregates every CO2 reading for a room into:
 *   - average and maximum CO2
 *   - minutes spent above the configured CO2 limit
 *
 * The result is persisted so it stays stable for reporting and replay, and is
 * also returned directly for current-day queries.
 */
@Service
public class DailyExposureService {

    private final SensorReadingRepository readingRepo;
    private final DailyExposureSummaryRepository summaryRepo;
    private final ThresholdSettings thresholds;

    public DailyExposureService(SensorReadingRepository readingRepo,
                                DailyExposureSummaryRepository summaryRepo,
                                ThresholdSettings thresholds) {
        this.readingRepo = readingRepo;
        this.summaryRepo = summaryRepo;
        this.thresholds = thresholds;
    }

    public DailyExposureSummary computeForDate(String roomId, LocalDate date) {
        // Use persisted value if we already computed it.
        return summaryRepo.findByRoomIdAndSummaryDate(roomId, date)
                .orElseGet(() -> buildAndSave(roomId, date));
    }

    private DailyExposureSummary buildAndSave(String roomId, LocalDate date) {
        Instant from = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<SensorReadingEntity> co2 = readingRepo
                .findByRoomIdAndRecordedAtBetweenOrderByRecordedAtAsc(roomId, from, to)
                .stream().filter(r -> "co2".equals(r.getMetric())).toList();

        double co2Avg = co2.stream().mapToDouble(SensorReadingEntity::getValue).average().orElse(0);
        double co2Max = co2.stream().mapToDouble(SensorReadingEntity::getValue).max().orElse(0);

        // Approximate minutes above limit: each reading represents one sample
        // interval. Use the average sampling interval if known.
        long minutesAbove = 0;
        if (co2.size() > 1) {
            long spanMs = java.time.Duration.between(co2.get(0).getRecordedAt(),
                    co2.get(co2.size() - 1).getRecordedAt()).toMillis();
            double intervalMs = spanMs / (double) (co2.size() - 1);
            long above = co2.stream().filter(r -> r.getValue() >= thresholds.getCo2Ppm()).count();
            minutesAbove = (long) Math.round(above * intervalMs / 60000.0);
        }

        DailyExposureSummary summary = new DailyExposureSummary();
        summary.setRoomId(roomId);
        summary.setSummaryDate(date);
        summary.setAvgCo2Ppm(Math.round(co2Avg * 10.0) / 10.0);
        summary.setMaxCo2Ppm(Math.round(co2Max * 10.0) / 10.0);
        summary.setMinutesAboveCo2Limit(minutesAbove);
        summary.setComputedAt(Instant.now());
        return summaryRepo.save(summary);
    }

    public List<DailyExposureSummary> recentRoomSummary(String roomId) {
        return summaryRepo.findByRoomIdOrderBySummaryDateDesc(roomId);
    }
}
