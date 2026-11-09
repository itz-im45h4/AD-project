package com.ridelink.farepayment.repository;

import com.ridelink.farepayment.domain.Payment;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for persisted Payment transaction records.
 */
public interface PaymentRepository extends MongoRepository<Payment, String> {

    /** Finds the payment transaction associated with a given ride ID. */
    Optional<Payment> findByRideId(String rideId);
}
