package com.ridelink.farepayment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * MongoDB document representing a finalized fare record.
 *
 * <p><strong>Data Boundary:</strong>
 * {@code rideId} is a logical foreign key referencing the Ride document in Ride Management Service.
 * Fare amount is stored as a high-precision {@link BigDecimal}.</p>
 */
@Document("fares")
public class Fare {

    @Id
    private String id;
    private String rideId;
    private BigDecimal amount;
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRideId() {
        return rideId;
    }

    public void setRideId(String rideId) {
        this.rideId = rideId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
