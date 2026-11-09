package com.ridelink.farepayment.repository;

import com.ridelink.farepayment.domain.Receipt;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for persisted payment receipt records.
 */
public interface ReceiptRepository extends MongoRepository<Receipt, String> {

    /** Finds the audit receipt associated with a given ride ID. */
    Optional<Receipt> findByRideId(String rideId);
}
