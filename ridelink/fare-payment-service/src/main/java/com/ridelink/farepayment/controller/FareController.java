package com.ridelink.farepayment.controller;

import com.ridelink.farepayment.dto.FareDtos.EstimateRequest;
import com.ridelink.farepayment.dto.FareDtos.EstimateResponse;
import com.ridelink.farepayment.dto.FareDtos.FareResponse;
import com.ridelink.farepayment.dto.FareDtos.FinalizeRequest;
import com.ridelink.farepayment.dto.FareDtos.PaymentRequest;
import com.ridelink.farepayment.dto.FareDtos.PaymentResponse;
import com.ridelink.farepayment.dto.FareDtos.ReceiptResponse;
import com.ridelink.farepayment.service.FareService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Fare Calculation, Payment Settlement, and Receipt Retrieval.
 *
 * <p><strong>Architectural Role:</strong>
 * Serves as the HTTP presentation adapter. Validates incoming payload data and delegates
 * financial business logic to {@link FareService}.</p>
 */
@RestController
@RequestMapping("/api/v1")
public class FareController {

    private final FareService fareService;

    public FareController(FareService fareService) {
        this.fareService = fareService;
    }

    /**
     * Calculates an upfront fare estimate before trip inception.
     *
     * @param request Validated distance and estimated duration
     * @return Itemized fare estimation with base and variable rates
     */
    @PostMapping("/fares/estimate")
    public EstimateResponse estimate(@Valid @RequestBody EstimateRequest request) {
        return fareService.estimate(request);
    }

    /**
     * Finalizes and records the final fare upon trip completion.
     *
     * <p>Invoked synchronously by Ride Management Service during ride completion.</p>
     *
     * @param request Final recorded distance and duration metrics
     * @return Finalized fare record
     */
    @PostMapping("/fares/finalize")
    public FareResponse finalizeFare(@Valid @RequestBody FinalizeRequest request) {
        return fareService.finalize(request);
    }

    /**
     * Executes simulated payment processing and triggers RabbitMQ event emission.
     *
     * @param request Payment details including fareId and payment method
     * @return Payment confirmation details
     */
    @PostMapping("/payments")
    public PaymentResponse payment(@Valid @RequestBody PaymentRequest request) {
        return fareService.payment(request);
    }

    /**
     * Retrieves the finalized fare record for a given ride ID.
     *
     * @param rideId Target ride unique identifier
     * @return Finalized fare record
     */
    @GetMapping("/fares/{rideId}")
    public FareResponse getFare(@PathVariable String rideId) {
        return fareService.getFare(rideId);
    }

    /**
     * Retrieves the payment receipt for a completed ride.
     *
     * @param rideId Target ride unique identifier
     * @return Itemized receipt record
     */
    @GetMapping("/receipts/{rideId}")
    public ReceiptResponse receipt(@PathVariable String rideId) {
        return fareService.receipt(rideId);
    }
}
