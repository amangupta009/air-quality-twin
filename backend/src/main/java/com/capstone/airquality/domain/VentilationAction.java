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
 * Module 5: the audit trail. Every "Ventilation ON/OFF" click from the
 * dashboard lands here with WHO did it and WHEN - immutable evidence.
 */
@Entity
@Table(name = "ventilation_actions", indexes = {
        @Index(name = "idx_ventaction_room_time", columnList = "roomId, actedAt")
})
public class VentilationAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String roomId;

    @Column(nullable = false, length = 8) // "ON" or "OFF"
    private String action;

    @Column(nullable = false)
    private String actor;

    @Column(length = 1000) // plain varchar, NOT @Lob: Postgres large objects break auto-commit reads
    private String note;

    @Column(nullable = false)
    private Instant actedAt;

    public Long getId() {
        return id;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getActedAt() {
        return actedAt;
    }

    public void setActedAt(Instant actedAt) {
        this.actedAt = actedAt;
    }
}
