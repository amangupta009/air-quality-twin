package com.capstone.airquality.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Module 1 data: one raw (post-calibration) sensor measurement.
 * This is the "timestamped table" time-series trade-off: a plain indexed
 * PostgreSQL table instead of a dedicated time-series database.
 * The composite index keeps range queries (replay, daily summaries) fast.
 */
@Entity
@Table(name = "sensor_readings", indexes = {
        @Index(name = "idx_reading_room_metric_time", columnList = "roomId, metric, recordedAt")
})
public class SensorReadingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String roomId;

    @Column(nullable = false, length = 16)
    private String metric; // co2 or occupancy

    @Column(nullable = false)
    private double value;

    @Column(nullable = false, length = 12)
    private String unit;

    @Column(nullable = false)
    private Instant recordedAt;

    public Long getId() {
        return id;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }
}
