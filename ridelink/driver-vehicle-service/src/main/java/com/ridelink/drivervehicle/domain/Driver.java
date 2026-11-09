package com.ridelink.drivervehicle.domain;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * MongoDB document representing an active driver operational record.
 *
 * <p><strong>Data Boundary Note:</strong>
 * {@code userId} references an Account entity owned by Account Service.
 * Driver operational status, current location coordinates, and vehicle details
 * are owned exclusively by Driver &amp; Vehicle Service.</p>
 */
@Document("drivers")
public class Driver {

    @Id
    private String id;
    private String userId;
    private String licenseNumber;
    private String serviceZone;
    private Vehicle vehicle;
    private Availability availability;
    private Double latitude;
    private Double longitude;
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = licenseNumber;
    }

    public String getServiceZone() {
        return serviceZone;
    }

    public void setServiceZone(String serviceZone) {
        this.serviceZone = serviceZone;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    public Availability getAvailability() {
        return availability;
    }

    public void setAvailability(Availability availability) {
        this.availability = availability;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
