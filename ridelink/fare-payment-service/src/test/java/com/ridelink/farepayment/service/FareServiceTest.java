package com.ridelink.farepayment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ridelink.farepayment.domain.Fare;
import com.ridelink.farepayment.domain.Payment;
import com.ridelink.farepayment.domain.PaymentStatus;
import com.ridelink.farepayment.domain.Receipt;
import com.ridelink.farepayment.dto.FareDtos.*;
import com.ridelink.farepayment.exception.ApiException;
import com.ridelink.farepayment.messaging.PaymentRecorded;
import com.ridelink.farepayment.repository.FareRepository;
import com.ridelink.farepayment.repository.PaymentRepository;
import com.ridelink.farepayment.repository.ReceiptRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class FareServiceTest {

    @Mock private FareRepository fares;
    @Mock private PaymentRepository payments;
    @Mock private ReceiptRepository receipts;
    @Mock private RabbitTemplate rabbit;

    private FareService service;

    @BeforeEach
    void setUp() {
        service = new FareService(fares, payments, receipts, rabbit);
    }

    @Test
    @DisplayName("Should estimate fare using documented formula: 200 + (km * 50) + (min * 10)")
    void estimateFare_calculatesAccurately() {
        EstimateRequest request = new EstimateRequest("Location A", "Location B",
                new BigDecimal("5.0"), new BigDecimal("10.0"));

        EstimateResponse response = service.estimate(request);

        assertThat(response).isNotNull();
        // 200 + 5*50 + 10*10 = 200 + 250 + 100 = 550.00
        assertThat(response.estimatedFare()).isEqualTo(new BigDecimal("550.00"));
        assertThat(response.currency()).isEqualTo("LKR");
        assertThat(response.baseFare()).isEqualTo(new BigDecimal("200.00"));
        assertThat(response.perKmRate()).isEqualTo(new BigDecimal("50.00"));
        assertThat(response.perMinuteRate()).isEqualTo(new BigDecimal("10.00"));
    }

    @Test
    @DisplayName("Should finalize fare and return PaymentStatus.PENDING")
    void finalizeFare_success() {
        FinalizeRequest request = new FinalizeRequest("ride-1",
                new BigDecimal("5.0"), new BigDecimal("10.0"));

        when(fares.findByRideId("ride-1")).thenReturn(Optional.empty());
        when(fares.save(any(Fare.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FareResponse response = service.finalize(request);

        assertThat(response.rideId()).isEqualTo("ride-1");
        assertThat(response.finalFare()).isEqualTo(new BigDecimal("550.00"));
        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("Should record successful payment, persist receipt, and publish RabbitMQ event")
    void recordPayment_success() {
        PaymentRequest request = new PaymentRequest("ride-1", "fare-1", "CARD");

        Fare fare = new Fare();
        fare.setId("fare-1");
        fare.setRideId("ride-1");
        fare.setAmount(new BigDecimal("550.00"));

        when(fares.findById("fare-1")).thenReturn(Optional.of(fare));
        when(payments.findByRideId("ride-1")).thenReturn(Optional.empty());
        when(payments.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = service.payment(request);

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.amount()).isEqualTo(new BigDecimal("550.00"));

        verify(receipts).save(any(Receipt.class));

        ArgumentCaptor<PaymentRecorded> eventCaptor = ArgumentCaptor.forClass(PaymentRecorded.class);
        verify(rabbit).convertAndSend(eq("ridelink.payment"), eq("payment.recorded"), eventCaptor.capture());

        PaymentRecorded event = eventCaptor.getValue();
        assertThat(event.rideId()).isEqualTo("ride-1");
        assertThat(event.status()).isEqualTo("SUCCESS");
        assertThat(event.amount()).isEqualTo(new BigDecimal("550.00"));
    }

    @Test
    @DisplayName("Should record FAILED payment when method is FAIL and publish FAILED event")
    void recordPayment_simulatedFailure() {
        PaymentRequest request = new PaymentRequest("ride-1", "fare-1", "FAIL");

        Fare fare = new Fare();
        fare.setId("fare-1");
        fare.setRideId("ride-1");
        fare.setAmount(new BigDecimal("550.00"));

        when(fares.findById("fare-1")).thenReturn(Optional.of(fare));
        when(payments.findByRideId("ride-1")).thenReturn(Optional.empty());
        when(payments.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = service.payment(request);

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);

        ArgumentCaptor<PaymentRecorded> eventCaptor = ArgumentCaptor.forClass(PaymentRecorded.class);
        verify(rabbit).convertAndSend(eq("ridelink.payment"), eq("payment.recorded"), eventCaptor.capture());

        assertThat(eventCaptor.getValue().status()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("Should reject payment if fare does not belong to the ride with 404 Not Found")
    void recordPayment_fareMismatch() {
        PaymentRequest request = new PaymentRequest("ride-1", "fare-1", "CARD");

        Fare fare = new Fare();
        fare.setId("fare-1");
        fare.setRideId("different-ride-id"); // mismatch!

        when(fares.findById("fare-1")).thenReturn(Optional.of(fare));

        assertThatThrownBy(() -> service.payment(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Fare not found for this ride")
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Should successfully retrieve finalized fare by ride ID")
    void getFare_success() {
        Fare fare = new Fare();
        fare.setId("fare-1");
        fare.setRideId("ride-1");
        fare.setAmount(new BigDecimal("550.00"));

        when(fares.findByRideId("ride-1")).thenReturn(Optional.of(fare));

        FareResponse response = service.getFare("ride-1");

        assertThat(response).isNotNull();
        assertThat(response.fareId()).isEqualTo("fare-1");
        assertThat(response.rideId()).isEqualTo("ride-1");
        assertThat(response.finalFare()).isEqualTo(new BigDecimal("550.00"));
    }

    @Test
    @DisplayName("Should return 404 NOT_FOUND when fare does not exist for ride ID")
    void getFare_notFound() {
        when(fares.findByRideId("unknown-ride")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getFare("unknown-ride"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Fare not found for this ride")
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
