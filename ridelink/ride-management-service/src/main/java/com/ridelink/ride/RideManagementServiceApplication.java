package com.ridelink.ride;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the independently runnable ride-orchestration microservice (default port 8083). */
@SpringBootApplication
public class RideManagementServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(RideManagementServiceApplication.class, args);
	}

}
