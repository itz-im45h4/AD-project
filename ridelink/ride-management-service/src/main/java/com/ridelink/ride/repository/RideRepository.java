package com.ridelink.ride.repository;

import com.ridelink.ride.domain.Ride;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

/**
 * Spring Data MongoDB repository for Ride records.
 *
 * <p>Provides multi-index querying across passenger and driver account references.</p>
 */
public interface RideRepository extends MongoRepository<Ride, String> {

    /**
     * Finds all rides where the specified user ID participated as passenger or driver.
     * Matches either {@code passengerId}, {@code driverUserId}, or {@code driverId}.
     */
    @Query("{ $or: [ { passengerId: ?0 }, { driverUserId: ?0 }, { driverId: ?0 } ] }")
    List<Ride> findForUser(String userId);
}
