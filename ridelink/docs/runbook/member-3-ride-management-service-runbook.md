# Member 3 — Ride Management Service: Operational Runbook

This runbook provides complete, step-by-step instructions for configuring, launching, verifying, and troubleshooting the **Ride Management Service** (`ride-management-service`).

---

## 1. Service Overview & Data Ownership

- **Service Name:** `ride-management-service`
- **Default Port:** `8083`
- **Database:** MongoDB Atlas `ride_mgmt_db`
- **Primary Collection:** `rides`
- **Ownership Scope:** Ride lifecycle finite state machine (`REQUESTED`, `ASSIGNED`, `ACCEPTED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`), ride creation, automated driver matching dispatch, distance/duration completion, ride history query, and eventual consistency payment reconciliation.
- **Architectural Boundary:**
  - **Synchronous Outbound REST:** Calls Driver & Vehicle Service (`GET /api/v1/drivers/available?zone=...`) for eligible drivers and Fare & Payment Service (`POST /api/v1/fares/finalize`) for final fare calculation.
  - **Asynchronous Inbound AMQP:** Listens to RabbitMQ queue `ridelink.payment-recorded` to asynchronously transition ride `paymentStatus` from `PENDING` to `SUCCESS` or `FAILED`.
  - **Data References:** Stores opaque foreign references: `passengerId` (from Account Service), `driverId` (from Driver Service), `fareId` (from Fare Service).

---

## 2. Prerequisites

Ensure your environment satisfies the following before launching:
- **Java Development Kit (JDK):** Version 21 (`java -version`).
- **Build Tool:** Apache Maven 3.9+ or the included Maven Wrapper (`./mvnw`).
- **MongoDB Atlas Cluster:** An active MongoDB Atlas connection URI pointing to `ride_mgmt_db`.
- **Shared Secret:** The exact same 32+ character HMAC-SHA256 secret key.
- **RabbitMQ Instance:** Active CloudAMQP or local RabbitMQ instance URL.
- **Service Dependency Order:** Start Account Service (8081), Driver Service (8082), and Fare & Payment Service (8084) *before* starting Ride Management. Fare & Payment declares the `ridelink.payment` exchange and queue bindings.

---

## 3. Configuration Setup

Create the local configuration file:

**File Path:** `ridelink/ride-management-service/src/main/resources/application-local.properties`

```properties
# MongoDB Atlas Connection (ride_mgmt_db scoped)
spring.mongodb.uri=mongodb+srv://<username>:<password>@<cluster>.mongodb.net/ride_mgmt_db?retryWrites=true&w=majority

# Shared HMAC-SHA256 Secret (Must match Account Service)
app.security.jwt-secret=your-shared-256-bit-secret-key-ridelink-2026-production

# Interservice Synchronous REST Clients
app.services.driver-vehicle.base-url=http://localhost:8082
app.services.fare-payment.base-url=http://localhost:8084

# RabbitMQ / CloudAMQP Connection
spring.rabbitmq.addresses=amqps://<username>:<password>@<host>/<vhost>

# Server Port
server.port=8083
```

> [!IMPORTANT]
> Ensure `spring.rabbitmq.addresses` matches exactly between Ride Management and Fare & Payment.

---

## 4. How to Run the Service

Navigate to `ridelink/`:

### Method 1: Using the Maven Wrapper (Standard)
```bash
cd ridelink
./mvnw -pl ride-management-service spring-boot:run -Dspring-boot.run.profiles=local
```

### Method 2: Behind a Network Proxy (If Applicable)
```bash
cd ridelink
./mvnw -s ../plan/maven-settings.xml -pl ride-management-service spring-boot:run -Dspring-boot.run.profiles=local
```

### Method 3: Using System Maven
```bash
cd ridelink
mvn -pl ride-management-service spring-boot:run -Dspring-boot.run.profiles=local
```

### Method 4: Running the Pre-Packaged JAR
```bash
cd ridelink
java -Dspring.profiles.active=local -jar ride-management-service/target/ride-management-service-0.0.1-SNAPSHOT.jar
```

---

## 5. Startup Verification & Ready Checks

### 1. Console Output Confirmation
Initialization is complete when you see Tomcat started and the RabbitMQ listener container connected:
```text
[main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port 8083 (http) with context path '/'
[main] o.s.a.r.l.SimpleMessageListenerContainer : SimpleMessageListenerContainer [queueNames=[ridelink.payment-recorded]] container started
[main] c.r.r.RideManagementServiceApplication   : Started RideManagementServiceApplication in 3.842 seconds
```

### 2. Swagger UI Ready Check
Open your browser and navigate to:
**`http://localhost:8083/swagger-ui/index.html`**

Confirm that:
1. Title displays `"RideLink Ride Management Service API"`.
2. The green **Authorize** padlock button is visible.
3. Endpoints under `/api/v1/rides` are listed (`POST /rides`, `/assign`, `/accept`, `/start`, `/complete`, `/cancel`, `GET /rides`).

### 3. Actuator Health Check
```bash
curl -s http://localhost:8083/actuator/health
```
**Expected Response:** `{"status":"UP"}`

---

## 6. Troubleshooting & Common Issues

| Issue / Error | Root Cause | Solution |
| :--- | :--- | :--- |
| `Port 8083 was already in use` | Another process is holding port 8083. | Run `lsof -i :8083` and `kill -9 <PID>`. |
| `RabbitMQ: SimpleMessageListenerContainer could not connect` | CloudAMQP URL incorrect, network firewall blocking AMQP port 5671/5672, or RabbitMQ cluster down. | Check `spring.rabbitmq.addresses`. Verify CloudAMQP dashboard is online. |
| `409 Conflict: No available drivers found in pickup zone` | Ride assignment attempted when no driver is `ONLINE` in the ride's `serviceZone`. | Have Member 2 register/set a driver to `ONLINE` in that zone (e.g. `Colombo`) before requesting assignment. |
| `500 or Interservice Connection Refused on complete` | Fare & Payment Service (`localhost:8084`) is not running when completing a ride. | Ride completion calls Fare Service to finalize the bill. Ensure Fare & Payment Service is running on port 8084. |
| `409 Conflict: Invalid state transition` | Attempting to start before accepting, or completing before starting. | Strictly follow the lifecycle order: `REQUESTED` $\rightarrow$ `ASSIGNED` $\rightarrow$ `ACCEPTED` $\rightarrow$ `IN_PROGRESS` $\rightarrow$ `COMPLETED`. |

---

## 7. Graceful Shutdown

To stop the running service:
- Press **`Ctrl + C`** in the terminal running Ride Management Service.
- The RabbitMQ listener container will gracefully unsubscribe from `ridelink.payment-recorded` without dropping unacknowledged messages.
