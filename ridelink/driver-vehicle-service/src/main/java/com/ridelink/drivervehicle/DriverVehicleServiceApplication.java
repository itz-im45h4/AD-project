package com.ridelink.drivervehicle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the independently runnable driver-operational microservice (default port 8082). */
@SpringBootApplication
public class DriverVehicleServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(DriverVehicleServiceApplication.class, args);
	}

}
