# Member 3 — Ride Management Service: API Testing & Viva Demonstration Guide

> **Microservice:** `ride-management-service` (Port: `8083`)  
> **Database:** MongoDB Atlas `ride_mgmt_db` (Collection: `rides`)  
> **Domain Responsibility:** Ride creation, finite state machine lifecycle transitions, automated driver dispatch, ride completion calculation, ride history query, and event-driven payment status updates.  
> **Interactive Documentation:** `http://localhost:8083/swagger-ui/index.html`  
> **Postman Folder:** `3. Ride Management Service` (Requests 3.1 to 3.8, plus End-to-End flows 5.1 to 5.7)

---

## 1. Architectural Role & Dual-Communication Paradigm

Ride Management Service sits at the operational center of RideLink. It demonstrates **both communication styles**:
1. **Synchronous REST Communication (`RestClient` with Bearer Token Propagation):**
   - Calls **Driver & Vehicle Service** (`GET /api/v1/drivers/available?zone=...`) to query candidate drivers during automated dispatch.
   - Calls **Fare & Payment Service** (`POST /api/v1/fares/finalize`) to calculate and store the final trip cost upon completion.
2. **Asynchronous AMQP Messaging (RabbitMQ / CloudAMQP):**
   - Subscribes via `@RabbitListener` to queue `ridelink.payment-recorded`.
   - When a passenger completes payment in Fare & Payment Service, an asynchronous `PaymentRecorded` event is published. Ride Management consumes this event and updates `paymentStatus` from `PENDING` to `SUCCESS` or `FAILED` (Eventual Consistency).
3. **Data Ownership:** Owns ride documents only (`ride_mgmt_db`). Stores opaque foreign keys: `passengerId`, `driverId`, and `fareId`.

---

## 2. Finite State Machine Lifecycle

```
[REQUESTED] ──(assign)──> [ASSIGNED] ──(accept)──> [ACCEPTED] ──(start)──> [IN_PROGRESS] ──(complete)──> [COMPLETED]
     │                         │                      │
  (cancel)                  (cancel)               (cancel)
     │                         │                      │
     ▼                         ▼                      ▼
[CANCELLED]               [CANCELLED]            [CANCELLED]
```

- Invalid transitions (e.g. attempting to start before acceptance, or cancelling an in-progress ride) immediately return `409 Conflict`.

---

## 3. API Endpoint Breakdown (Deep-Dive & Use Cases)

### 3.1 Request a New Ride
- **HTTP Method & Path:** `POST /api/v1/rides`
- **Use Case:** A passenger opens the app, enters pickup and destination locations, and requests a ride.
- **Security:** Requires `ROLE_PASSENGER`. Caller's `userId` must match `request.passengerId()`.
- **Core Business Logic:**
  1. Validates coordinate bounds for both pickup and destination.
  2. Creates new `Ride` document in initial status `REQUESTED` and `paymentStatus: "PENDING"`.
  3. Returns `201 Created` with `Location: /api/v1/rides/{id}` header.
- **Request Body Example:**
  ```json
  {
    "passengerId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "pickup": {
      "latitude": 6.9271,
      "longitude": 79.8612,
      "address": "Colombo Fort Station",
      "zone": "Colombo"
    },
    "destination": {
      "latitude": 6.9056,
      "longitude": 79.8607,
      "address": "Colombo National Museum",
      "zone": "Colombo"
    }
  }
  ```
- **Response (`201 Created`):** Returns ride document with `status: "REQUESTED"`, `paymentStatus: "PENDING"`.

---

### 3.2 Assign Driver (Automated Dispatch)
- **HTTP Method & Path:** `POST /api/v1/rides/{rideId}/assign`
- **Use Case:** The passenger or platform triggers automated driver matching.
- **Security:** Requires `ROLE_PASSENGER` and ride ownership.
- **Core Business Logic:**
  1. Verifies ride status is `REQUESTED`.
  2. Extracts pickup zone (`Colombo`).
  3. **Interservice REST Call:** Forwards caller's Bearer token and calls Driver Service: `GET http://localhost:8082/api/v1/drivers/available?zone=Colombo`.
  4. If candidate list is empty, throws `409 Conflict` (`"No available drivers found in pickup zone"`).
  5. Selects first online driver, assigns `driverId`, and transitions status to `ASSIGNED`.
- **Response (`200 OK`):**
  ```json
  {
    "id": "ride-uuid-1234",
    "status": "ASSIGNED",
    "driverId": "drv-550e8400-e29b-41d4-a716-446655440000",
    "passengerId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
  }
  ```

---

### 3.3 Driver Accepts Ride
- **HTTP Method & Path:** `POST /api/v1/rides/{rideId}/accept`
- **Use Case:** The assigned driver receives a trip notification and taps "Accept".
- **Security:** Requires `ROLE_DRIVER` matching the assigned `driverId`.
- **Core Business Logic:**
  1. Verifies current status is `ASSIGNED`.
  2. Verifies authenticated driver is the assigned driver.
  3. Transitions status to `ACCEPTED`.
- **Response (`200 OK`):** Returns ride with `status: "ACCEPTED"`.

---

### 3.4 Driver Starts Ride
- **HTTP Method & Path:** `POST /api/v1/rides/{rideId}/start`
- **Use Case:** Driver arrives at pickup, passenger boards, and driver starts the meter.
- **Security:** Requires `ROLE_DRIVER` matching assigned driver.
- **Core Business Logic:**
  1. Verifies current status is `ACCEPTED`.
  2. Transitions status to `IN_PROGRESS`.
- **Response (`200 OK`):** Returns ride with `status: "IN_PROGRESS"`.

---

### 3.5 Driver Completes Ride (Final Fare Interservice Call)
- **HTTP Method & Path:** `POST /api/v1/rides/{rideId}/complete`
- **Use Case:** Trip ends at destination. Driver records final trip odometer distance and elapsed minutes.
- **Security:** Requires `ROLE_DRIVER` matching assigned driver.
- **Core Business Logic:**
  1. Verifies status is `IN_PROGRESS`.
  2. **Interservice REST Call:** Calls Fare & Payment Service: `POST http://localhost:8084/api/v1/fares/finalize` passing `rideId`, `passengerId`, `driverId`, `distanceKm`, and `durationMinutes`.
  3. Receives finalized fare amount and `fareId`.
  4. Stores `finalFare` and `fareId`, and transitions status to `COMPLETED`.
- **Request Body Example:**
  ```json
  {
    "distanceKm": 8.5,
    "durationMinutes": 22
  }
  ```
- **Response (`200 OK`):**
  ```json
  {
    "id": "ride-uuid-1234",
    "status": "COMPLETED",
    "fareId": "fare-uuid-5678",
    "finalFare": 845.00,
    "paymentStatus": "PENDING"
  }
  ```

---

### 3.6 Cancel Ride
- **HTTP Method & Path:** `POST /api/v1/rides/{rideId}/cancel`
- **Use Case:** Passenger or driver cancels trip before it begins.
- **Security:** Authenticated participant (passenger or assigned driver).
- **Core Business Logic:**
  1. Validates that current status is `REQUESTED`, `ASSIGNED`, or `ACCEPTED`.
  2. If already `IN_PROGRESS` or `COMPLETED`, throws `409 Conflict`.
  3. Transitions status to `CANCELLED`.

---

### 3.7 Query Ride History
- **HTTP Method & Path:** `GET /api/v1/rides?userId={userId}`
- **Use Case:** User views past trips in their ride history.
- **Security:** Authenticated (caller must match `userId` or have `ROLE_ADMIN`).
- **Core Business Logic:**
  1. Executes MongoDB `$or` query: `findRidesForUser(userId)` matching either `passengerId == userId` or `driverId == userId`.
- **Response (`200 OK`):** Array of ride records.

---

### 3.8 Asynchronous Event Consumption (RabbitMQ)
- **Component:** [`PaymentRecordedListener.java`](file:///home/im45h4/Documents/Y3S1/Application%20Development/project/code/ridelink/ride-management-service/src/main/java/com/ridelink/ride/messaging/PaymentRecordedListener.java)
- **Queue:** `ridelink.payment-recorded`
- **Mechanism:**
  - Listens for JSON message: `{"rideId":"...","status":"SUCCESS",...}`.
  - Updates matching ride's `paymentStatus` to `SUCCESS` or `FAILED`.

---

## 4. How to Test via Swagger UI

1. Open **`http://localhost:8083/swagger-ui/index.html`**.
2. **Authorize with Passenger Token:**
   - Click green **Authorize** padlock button, paste `Bearer <passenger-token>`.
3. **Request Ride:**
   - Execute `POST /api/v1/rides`. Save returned `id` (`rideId`).
4. **Assign Driver:**
   - Execute `POST /api/v1/rides/{rideId}/assign`. Confirm status transitions to `ASSIGNED` and a `driverId` is attached.
5. **Switch Authorization to Driver Token:**
   - Authorize with `Bearer <driver-token>`.
6. **Accept & Start:**
   - Execute `POST /api/v1/rides/{rideId}/accept` $\rightarrow$ `200 OK` (`ACCEPTED`).
   - Execute `POST /api/v1/rides/{rideId}/start` $\rightarrow$ `200 OK` (`IN_PROGRESS`).
7. **Complete Ride:**
   - Execute `POST /api/v1/rides/{rideId}/complete` with `{"distanceKm": 8.5, "durationMinutes": 22}`.
   - Confirm status is `COMPLETED`, `finalFare` is populated (`845.00`), and `paymentStatus` is `PENDING`.
8. **Verify Eventual Payment Update:**
   - After processing payment on Port 8084, execute `GET /api/v1/rides?userId=<passengerId>`.
   - Confirm `paymentStatus` has transitioned from `PENDING` to `SUCCESS` via RabbitMQ!

---

## 5. Concrete Test Cases Matrix

| Test ID | Method & URL | Test Intent | Conditions / Input | Expected Status | Key Assertion / Event Check |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-RD-01** | `POST /api/v1/rides` | Happy Path Ride Request | Valid passengerId, pickup/dest | `201 Created` | Status `REQUESTED`, paymentStatus `PENDING`. |
| **TC-RD-02** | `POST /api/v1/rides/{id}/assign` | Interservice REST Dispatch | Driver online in pickup zone | `200 OK` | Status `ASSIGNED`, `driverId` populated from Driver Service. |
| **TC-RD-03** | `POST /api/v1/rides/{id}/assign` | Interservice No-Driver Guard | No driver online in zone | `409 Conflict` | Detail: `"No available drivers found in pickup zone"`. |
| **TC-RD-04** | `POST /api/v1/rides/{id}/accept` | Happy Path Driver Acceptance | Called by assigned driver | `200 OK` | Status `ACCEPTED`. |
| **TC-RD-05** | `POST /api/v1/rides/{id}/start` | Invalid State Guard | Called before ride is accepted | `409 Conflict` | Detail: `"Ride cannot be started in status: ASSIGNED"`. |
| **TC-RD-06** | `POST /api/v1/rides/{id}/start` | Happy Path Ride Start | Ride is in `ACCEPTED` state | `200 OK` | Status `IN_PROGRESS`. |
| **TC-RD-07** | `POST /api/v1/rides/{id}/complete` | Interservice Final Fare Call | Dist: 8.5 km, Dur: 22 mins | `200 OK` | Status `COMPLETED`, `finalFare: 845.00`, `fareId` stored. |
| **TC-RD-08** | `POST /api/v1/rides/{id}/cancel` | Happy Path Cancellation | Ride in `REQUESTED` state | `200 OK` | Status `CANCELLED`. |
| **TC-RD-09** | `POST /api/v1/rides/{id}/cancel` | State Guard: In-Progress | Ride in `IN_PROGRESS` state | `409 Conflict` | Cannot cancel an active in-progress ride. |
| **TC-RD-10** | `GET /api/v1/rides?userId=...` | User Trip History Query | Authenticated matching user | `200 OK` | Returns array of user's past and current rides. |
| **TC-RD-11** | RabbitMQ Consumer | Eventual Consistency Sync | `PaymentRecorded` event | Internal Async | `paymentStatus` updates from `PENDING` to `SUCCESS`. |

---

## 6. Viva Demonstration Walkthrough & Defense Script

### 6.1 The 60-Second Elevator Pitch
> *"Good morning. I am responsible for Member 3's deliverable: the Ride Management Service. This service orchestrates the core business transactions of RideLink. It manages a strict finite state machine transitioning rides through REQUESTED, ASSIGNED, ACCEPTED, IN_PROGRESS, and COMPLETED states. It exemplifies our dual-communication architecture: it makes synchronous REST calls with token propagation to Driver Service for dispatch and Fare Service for final billing, while using asynchronous RabbitMQ messaging to consume payment settlement events for eventual consistency. It owns the `ride_mgmt_db` database on MongoDB Atlas."*

### 6.2 Code Navigation Guide
1. Open [`RideService.java`](file:///home/im45h4/Documents/Y3S1/Application%20Development/project/code/ridelink/ride-management-service/src/main/java/com/ridelink/ride/service/RideService.java):
   - Highlight line 59 (`assign`): Show the synchronous `driverClient.getAvailableDrivers(zone, authHeader)` call.
   - Highlight line 105 (`complete`): Show the synchronous `fareClient.finalizeFare(..., authHeader)` call.
   - Highlight line 132 (`applyPaymentRecorded`): Show how payment status is reconciled.
2. Open [`PaymentRecordedListener.java`](file:///home/im45h4/Documents/Y3S1/Application%20Development/project/code/ridelink/ride-management-service/src/main/java/com/ridelink/ride/messaging/PaymentRecordedListener.java):
   - Show `@RabbitListener(queues = "ridelink.payment-recorded")` and message deserialization.

### 6.3 Top Examiner Viva Questions & Model Answers

**Q1: Why did you use synchronous REST for driver assignment but asynchronous RabbitMQ for payment recording?**  
*Model Answer:* Driver assignment is a blocking user-experience operation: the passenger is actively waiting on their screen to know if a car is found or if no drivers are available. Payment settlement, however, involves eventual consistency: once the payment service records the transaction and prints the receipt, notifying the ride management service can happen asynchronously in the background. If Ride Management is temporarily down, the durable RabbitMQ queue retains the message, ensuring zero data loss.

**Q2: What happens if an unauthorized driver attempts to accept someone else's assigned ride?**  
*Model Answer:* In `RideService.accept()`, we call `requireAssignedDriver(ride, auth)`. We compare the caller's authenticated driver ID against `ride.getDriverId()`. If there is a mismatch, we immediately throw a `403 Forbidden` error.

**Q3: How do you forward the caller's JWT token during interservice REST calls?**  
*Model Answer:* The controller captures `@RequestHeader("Authorization") String authHeader` and passes it to `RideService`. The `RestClient` in [`ClientConfig.java`](file:///home/im45h4/Documents/Y3S1/Application%20Development/project/code/ridelink/ride-management-service/src/main/java/com/ridelink/ride/config/ClientConfig.java) attaches this header to outgoing HTTP requests (`.header("Authorization", authHeader)`), preserving the caller's authenticated identity and security context across service boundaries.
