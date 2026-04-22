package com.enduser.enduserapp.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "location_updates")
public class LocationUpdate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String driverId;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false)
    private Instant receivedAt;

    protected LocationUpdate() {
    }

    public LocationUpdate(String driverId, double latitude, double longitude, Instant receivedAt) {
        this.driverId = driverId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.receivedAt = receivedAt;
    }

    public Long getId() { return id; }
    public String getDriverId() { return driverId; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public Instant getReceivedAt() { return receivedAt; }
}
