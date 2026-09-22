# RideLink — Backend Microservices

RideLink is the IT3130 Application Development group assignment: a backend-only ride-sharing platform made of four independently executable Java 21 / Spring Boot **4.1.1** microservices. Swagger UI and the shared Postman collection are the supported demo clients; no frontend is required.

## Project root and layout

The application build root is [`ridelink/`](ridelink/). Run Maven commands from that directory.

```text
ridelink/
├── pom.xml                         # Spring Boot 4.1.1 Maven reactor
├── account-service/                # :8081, account_db
├── driver-vehicle-service/         # :8082, driver_vehicle_db
├── ride-management-service/        # :8083, ride_mgmt_db
├── fare-payment-service/           # :8084, fare_payment_db
├── docs/                           # architecture, sequence, shared contracts
├── postman/                        # collection and local environment skeleton
```

Repository CI lives at `.github/workflows/ci.yml`, as required by GitHub Actions.

## Ownership and boundaries

| Owner | Service | Responsibility |
| --- | --- | --- |
| Member 1 -Minadi | Account | Identity, authentication, roles, profiles, account status |
| Member 2 -Kavidya | Driver & Vehicle | Driver readiness, vehicles, availability, location, eligible-driver lookup |
| Member 3 -Imasha| Ride Management | Ride lifecycle, driver assignment, orchestration |
| Member 4 -Dilneth| Fare & Payment | Estimates, final fares, simulated payments, receipts |

Driver & Vehicle answers **who is available**; Ride Management decides **who is assigned**. Fare & Payment owns monetary records; Ride Management owns ride state. Every service owns a separate MongoDB database and may only exchange IDs and documented API/event messages.

## Prerequisites and configuration

- Java 21
- MongoDB for local development or an isolated MongoDB Atlas database per service
- RabbitMQ/CloudAMQP for Ride Management and Fare & Payment once messaging is implemented

Copy the relevant service `.env.example` values into IDE/run-configuration environment variables. Do not commit `.env` files, database credentials, JWT secrets, or CloudAMQP URLs.

The baseline applications use local MongoDB defaults and these ports:

| Service | Port | Swagger URL |
| --- | ---: | --- |
| Account | 8081 | `http://localhost:8081/swagger-ui.html` |
| Driver & Vehicle | 8082 | `http://localhost:8082/swagger-ui.html` |
| Ride Management | 8083 | `http://localhost:8083/swagger-ui.html` |
| Fare & Payment | 8084 | `http://localhost:8084/swagger-ui.html` |

## Build, test, and run

```bash
cd ridelink
./mvnw verify

# Run services in separate terminals after their configuration is supplied.
./mvnw -pl account-service spring-boot:run
./mvnw -pl driver-vehicle-service spring-boot:run
./mvnw -pl fare-payment-service spring-boot:run
./mvnw -pl ride-management-service spring-boot:run
```

Run Fare & Payment before Ride Management when their synchronous integration is implemented. RabbitMQ is only needed once the `PaymentRecorded` consumer/publisher is implemented.

## Shared interfaces

All APIs use `/api/v1`, camelCase JSON, UUID string IDs, ISO-8601 UTC timestamps, and RFC 9457 Problem Details for errors. The initial conventions and service operations are in [`ridelink/docs/contracts/api-conventions.md`](ridelink/docs/contracts/api-conventions.md). The async `PaymentRecorded` schema is versioned alongside it.

## Collaboration workflow

Use `main` as the always-demonstrable branch and `develop` as the shared integration branch. Work on service-focused feature branches, open a pull request, obtain one peer review, and merge only after CI passes. GitHub Actions runs `./mvnw --batch-mode verify` for pull requests and pushes targeting `main` or `develop`.

The original assignment brief and team guidance are retained in [`instructions/`](instructions/).
