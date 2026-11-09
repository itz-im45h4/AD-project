package com.ridelink.drivervehicle.domain;

/**
 * Embedded domain object representing vehicle physical and operational specifications.
 * Stored as an embedded sub-document inside Driver MongoDB documents.
 */
public class Vehicle {

    private String make;
    private String model;
    private String plateNumber;
    private int capacity;

    public String getMake() {
        return make;
    }

    public void setMake(String make) {
        this.make = make;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getPlateNumber() {
        return plateNumber;
    }

    public void setPlateNumber(String plateNumber) {
        this.plateNumber = plateNumber;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }
}
