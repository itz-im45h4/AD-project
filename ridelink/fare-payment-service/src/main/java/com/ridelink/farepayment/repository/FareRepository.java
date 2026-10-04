package com.ridelink.farepayment.repository;

import com.ridelink.farepayment.domain.Fare;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for persisted Fare records.
 */
public interface FareRepository extends MongoRepository<Fare, String> {

    /** Finds the fare record associated with a given ride ID. */
    Optional<Fare> findByRideId(String rideId);
}
