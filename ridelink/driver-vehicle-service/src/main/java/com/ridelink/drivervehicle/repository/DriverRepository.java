package com.ridelink.drivervehicle.repository;

import com.ridelink.drivervehicle.domain.Availability;
import com.ridelink.drivervehicle.domain.Driver;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for Driver persistence and fleet querying.
 *
 * <p>Provides derived query methods for enforcing fleet uniqueness constraints
 * and performing geospatial/availability queries across drivers.</p>
 */
public interface DriverRepository extends MongoRepository<Driver, String> {

    /** Checks if a driver profile is already registered for this Account userId. */
    boolean existsByUserId(String userId);

    /** Checks if a driving license number is already registered in the system. */
    boolean existsByLicenseNumber(String licenseNumber);

    /** Checks if a vehicle license plate is already assigned to an existing driver. */
    boolean existsByVehiclePlateNumber(String plateNumber);

    /** Retrieves all drivers matching a specific operational status and zone. */
    List<Driver> findByAvailabilityAndServiceZone(Availability availability, String serviceZone);
}
