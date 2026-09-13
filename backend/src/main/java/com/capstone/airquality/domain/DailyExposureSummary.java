package com.capstone.airquality.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.Instant;

/**
 * Module 6 backing data: per-room daily exposure rollup
 * (avg/max CO2, minutes above limit).
 * Computed once per day by a scheduled job (added in a later phase).
 */
@Entity
@Table(name = "daily_exposure_summaries", indexes = {
        @Index(name = "idx_summary_room_date", columnList = "roomId, summaryDate")
})
public class DailyExposureSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String roomId;

    @Column(nullable = false)
    private LocalDate summaryDate;

    private Double avgCo2Ppm;
    private Double maxCo2Ppm;
    private long minutesAboveCo2Limit;

    @Column(nullable = false)
    private Instant computedAt;

    public Long getId() {
        return id;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public LocalDate getSummaryDate() {
        return summaryDate;
    }

    public void setSummaryDate(LocalDate summaryDate) {
        this.summaryDate = summaryDate;
    }

    public Double getAvgCo2Ppm() {
        return avgCo2Ppm;
    }

    public void setAvgCo2Ppm(Double avgCo2Ppm) {
        this.avgCo2Ppm = avgCo2Ppm;
    }

    public Double getMaxCo2Ppm() {
        return maxCo2Ppm;
    }

    public void setMaxCo2Ppm(Double maxCo2Ppm) {
        this.maxCo2Ppm = maxCo2Ppm;
    }

    public long getMinutesAboveCo2Limit() {
        return minutesAboveCo2Limit;
    }

    public void setMinutesAboveCo2Limit(long minutesAboveCo2Limit) {
        this.minutesAboveCo2Limit = minutesAboveCo2Limit;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(Instant computedAt) {
        this.computedAt = computedAt;
    }
}
