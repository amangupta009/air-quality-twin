package com.capstone.airquality.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A physical room being monitored (e.g. room101 = "Lab 101"). */
@Entity
@Table(name = "rooms")
public class Room {

    @Id
    private String id; // human-readable id used in MQTT topics too

    private String name;
    private int capacity;
    private double volumeM3;

    public Room() {
    }

    public Room(String id, String name, int capacity, double volumeM3) {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
        this.volumeM3 = volumeM3;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public double getVolumeM3() {
        return volumeM3;
    }

    public void setVolumeM3(double volumeM3) {
        this.volumeM3 = volumeM3;
    }
}
