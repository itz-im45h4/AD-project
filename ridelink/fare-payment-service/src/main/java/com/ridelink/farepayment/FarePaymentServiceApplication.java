package com.ridelink.farepayment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the independently runnable monetary microservice (default port 8084). */
@SpringBootApplication
public class FarePaymentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(FarePaymentServiceApplication.class, args);
	}

}
