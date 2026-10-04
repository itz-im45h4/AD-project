package com.ridelink.drivervehicle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ridelink.drivervehicle.domain.Availability;
import com.ridelink.drivervehicle.domain.Driver;
import com.ridelink.drivervehicle.domain.Vehicle;
import com.ridelink.drivervehicle.dto.DriverDtos.*;
import com.ridelink.drivervehicle.exception.ApiException;
import com.ridelink.drivervehicle.repository.DriverRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    @Mock private DriverRepository drivers;

    private DriverService service;

    @BeforeEach
    void setUp() {
        service = new DriverService(drivers);
    }

    private Authentication mockDriverAuth(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority("ROLE_DRIVER")));
    }

    @Test
    @DisplayName("Should successfully create driver operational profile")
    void createProfile_success() {
        Authentication auth = mockDriverAuth("user-driver-1");
        ProfileRequest request = new ProfileRequest("user-driver-1", "DL-12345",
                new VehicleRequest("Toyota", "Prius", "CAB-1122", 4), "ZONE_A");

        when(drivers.existsByUserId("user-driver-1")).thenReturn(false);
        when(drivers.existsByLicenseNumber("DL-12345")).thenReturn(false);
        when(drivers.existsByVehiclePlateNumber("CAB-1122")).thenReturn(false);
        when(drivers.save(any(Driver.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DriverResponse response = service.create(auth, request);

        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo("user-driver-1");
        assertThat(response.licenseNumber()).isEqualTo("DL-12345");
        assertThat(response.availability()).isEqualTo(Availability.OFFLINE);
        assertThat(response.serviceZone()).isEqualTo("ZONE_A");
        assertThat(response.vehicle().plateNumber()).isEqualTo("CAB-1122");
    }

    @Test
    @DisplayName("Should forbid profile creation if token does not match user ID")
    void createProfile_forbiddenDifferentUser() {
        Authentication auth = mockDriverAuth("another-driver");
        ProfileRequest request = new ProfileRequest("user-driver-1", "DL-12345",
                new VehicleRequest("Toyota", "Prius", "CAB-1122", 4), "ZONE_A");

        assertThatThrownBy(() -> service.create(auth, request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Only the owning driver may change this profile")
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Should reject duplicate vehicle plate with 409 Conflict")
    void createProfile_duplicatePlate() {
        Authentication auth = mockDriverAuth("user-driver-1");
        ProfileRequest request = new ProfileRequest("user-driver-1", "DL-12345",
                new VehicleRequest("Toyota", "Prius", "CAB-1122", 4), "ZONE_A");

        when(drivers.existsByUserId("user-driver-1")).thenReturn(false);
        when(drivers.existsByLicenseNumber("DL-12345")).thenReturn(false);
        when(drivers.existsByVehiclePlateNumber("CAB-1122")).thenReturn(true);

        assertThatThrownBy(() -> service.create(auth, request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists")
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Should successfully update driver availability to ONLINE")
    void updateAvailability_success() {
        Authentication auth = mockDriverAuth("user-driver-1");
        Driver driver = new Driver();
        driver.setId("driver-1");
        driver.setUserId("user-driver-1");
        driver.setAvailability(Availability.OFFLINE);
        driver.setVehicle(new Vehicle());

        when(drivers.findById("driver-1")).thenReturn(Optional.of(driver));
        when(drivers.save(any(Driver.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DriverResponse response = service.availability(auth, "driver-1", new AvailabilityRequest(Availability.ONLINE));

        assertThat(response.availability()).isEqualTo(Availability.ONLINE);
    }

    @Test
    @DisplayName("Should return available ONLINE drivers matching the requested zone")
    void availableDrivers_returnsMatches() {
        Driver d1 = new Driver();
        d1.setId("driver-1");
        d1.setUserId("user-1");
        d1.setAvailability(Availability.ONLINE);
        d1.setServiceZone("ZONE_A");
        d1.setVehicle(new Vehicle());

        when(drivers.findByAvailabilityAndServiceZone(Availability.ONLINE, "ZONE_A")).thenReturn(List.of(d1));

        List<DriverResponse> result = service.available("ZONE_A");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("driver-1");
    }

    @Test
    @DisplayName("Should successfully retrieve driver operational profile by ID")
    void getDriver_success() {
        Driver driver = new Driver();
        driver.setId("driver-1");
        driver.setUserId("user-1");
        driver.setLicenseNumber("DL-999");
        driver.setServiceZone("ZONE_A");
        driver.setAvailability(Availability.ONLINE);
        driver.setVehicle(new Vehicle());

        when(drivers.findById("driver-1")).thenReturn(Optional.of(driver));

        DriverResponse response = service.get("driver-1");

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo("driver-1");
        assertThat(response.licenseNumber()).isEqualTo("DL-999");
    }

    @Test
    @DisplayName("Should return 404 NOT_FOUND when driver ID does not exist")
    void getDriver_notFound() {
        when(drivers.findById("unknown-driver")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("unknown-driver"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Driver not found")
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
