package com.ridelink.drivervehicle.repository;
import java.util.List; import com.ridelink.drivervehicle.domain.*; import org.springframework.data.mongodb.repository.MongoRepository;
public interface DriverRepository extends MongoRepository<Driver,String> { boolean existsByUserId(String userId); boolean existsByLicenseNumber(String licenseNumber); boolean existsByVehiclePlateNumber(String plateNumber); List<Driver> findByAvailabilityAndServiceZone(Availability availability, String serviceZone); }
