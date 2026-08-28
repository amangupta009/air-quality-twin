package com.capstone.airquality.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Module 7 backing data: calibration correction applied to raw readings.
 * calibrated = raw * scaleValue + offsetValue
 * The most recent profile per (room, metric) is the active one.
 */
@Entity
@Table(name = "calibration_profiles")
public class CalibrationProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String roomId;

    @Column(nullable = false)
    private String metric;

    @Column(nullable = false)
    private double offsetValue;

    @Column(nullable = false)
    private double scaleValue;

    @Column(nullable = false)
    private String calibratedBy;

    @Column(nullable = false)
    private Instant calibratedAt;

    private String notes;

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

    public double getOffsetValue() {
        return offsetValue;
    }

    public void setOffsetValue(double offsetValue) {
        this.offsetValue = offsetValue;
    }

    public double getScaleValue() {
        return scaleValue;
    }

    public void setScaleValue(double scaleValue) {
        this.scaleValue = scaleValue;
    }

    public String getCalibratedBy() {
        return calibratedBy;
    }

    public void setCalibratedBy(String calibratedBy) {
        this.calibratedBy = calibratedBy;
    }

    public Instant getCalibratedAt() {
        return calibratedAt;
    }

    public void setCalibratedAt(Instant calibratedAt) {
        this.calibratedAt = calibratedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
