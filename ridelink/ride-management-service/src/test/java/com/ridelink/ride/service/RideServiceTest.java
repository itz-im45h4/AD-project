package com.ridelink.ride.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ridelink.ride.domain.Ride;
import com.ridelink.ride.domain.RideStatus;
import com.ridelink.ride.dto.RideDtos.*;
import com.ridelink.ride.exception.ApiException;
import com.ridelink.ride.repository.RideRepository;
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
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class RideServiceTest {

    @Mock private RideRepository rides;
    @Mock private RestClient.Builder clientBuilder;

    private RideService service;

    @BeforeEach
    void setUp() {
        service = new RideService(rides, clientBuilder, "http://localhost:8082", "http://localhost:8084");
    }

    private Authentication mockAuth(String userId, String role) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    @DisplayName("Should successfully create a ride request for passenger")
    void createRide_success() {
        Authentication auth = mockAuth("passenger-1", "PASSENGER");
        CreateRequest request = new CreateRequest("passenger-1",
                new Location("Pickup Point", "ZONE_A", 6.9271, 79.8612),
                new Location("Drop Point", "ZONE_A", 6.8939, 79.8547));

        when(rides.save(any(Ride.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RideResponse response = service.create(auth, request);

        assertThat(response).isNotNull();
        assertThat(response.passengerId()).isEqualTo("passenger-1");
        assertThat(response.status()).isEqualTo(RideStatus.REQUESTED);
        assertThat(response.paymentStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Should forbid ride creation if authenticated user does not match passengerId")
    void createRide_mismatchPassengerId() {
        Authentication auth = mockAuth("another-user", "PASSENGER");
        CreateRequest request = new CreateRequest("passenger-1",
                new Location("Pickup Point", "ZONE_A", 6.9271, 79.8612),
                new Location("Drop Point", "ZONE_A", 6.8939, 79.8547));

        assertThatThrownBy(() -> service.create(auth, request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Passenger ID must match the authenticated account")
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Should reject invalid state transition (start before accept) with 409 Conflict")
    void startRide_invalidTransition_conflict() {
        Authentication driverAuth = mockAuth("driver-user-1", "DRIVER");
        Ride ride = new Ride();
        ride.setId("ride-1");
        ride.setStatus(RideStatus.REQUESTED); // NOT accepted yet!
        ride.setDriverUserId("driver-user-1");

        when(rides.findById("ride-1")).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> service.start(driverAuth, "ride-1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid ride status transition")
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Should transition from ASSIGNED to ACCEPTED when assigned driver accepts")
    void acceptRide_success() {
        Authentication driverAuth = mockAuth("driver-user-1", "DRIVER");
        Ride ride = new Ride();
        ride.setId("ride-1");
        ride.setStatus(RideStatus.ASSIGNED);
        ride.setDriverUserId("driver-user-1");

        when(rides.findById("ride-1")).thenReturn(Optional.of(ride));
        when(rides.save(any(Ride.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RideResponse response = service.accept(driverAuth, "ride-1");

        assertThat(response.status()).isEqualTo(RideStatus.ACCEPTED);
    }

    @Test
    @DisplayName("Should forbid driver accept if another driver attempts to accept")
    void acceptRide_forbiddenDifferentDriver() {
        Authentication anotherDriverAuth = mockAuth("another-driver", "DRIVER");
        Ride ride = new Ride();
        ride.setId("ride-1");
        ride.setStatus(RideStatus.ASSIGNED);
        ride.setDriverUserId("driver-user-1");

        when(rides.findById("ride-1")).thenReturn(Optional.of(ride));

        assertThatThrownBy(() -> service.accept(anotherDriverAuth, "ride-1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Only the assigned driver may update this ride")
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Should allow passenger to cancel ride before it starts")
    void cancelRide_success() {
        Authentication auth = mockAuth("passenger-1", "PASSENGER");
        Ride ride = new Ride();
        ride.setId("ride-1");
        ride.setPassengerId("passenger-1");
        ride.setStatus(RideStatus.REQUESTED);

        when(rides.findById("ride-1")).thenReturn(Optional.of(ride));
        when(rides.save(any(Ride.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RideResponse response = service.cancel(auth, "ride-1");

        assertThat(response.status()).isEqualTo(RideStatus.CANCELLED);
    }

    @Test
    @DisplayName("Should update paymentStatus when asynchronous PaymentRecorded event arrives")
    void paymentRecorded_updatesStatus() {
        Ride ride = new Ride();
        ride.setId("ride-1");
        ride.setPaymentStatus("PENDING");

        when(rides.findById("ride-1")).thenReturn(Optional.of(ride));
        when(rides.save(any(Ride.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.paymentRecorded("ride-1", "SUCCESS");

        assertThat(ride.getPaymentStatus()).isEqualTo("SUCCESS");
    }
}
