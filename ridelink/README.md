# RideLink — Ride Management Service
### IT3130: Application Development — Individual Submission (Member 3)

---

## 1. Submission & Ownership Metadata

| Field | Details |
| :--- | :--- |
| **Institution** | Sri Lanka Institute of Information Technology (SLIIT) |
| **Faculty / Department** | Faculty of Computing — Department of Information Technology |
| **Module** | IT3130 — Application Development (Year 3, Semester 1) |
| **Assessment** | Group Assignment & Individual Microservice Ownership |
| **Assigned Member** | **Member 3** |
| **Student Full Name** | *[Student 3 Full Name]* |
| **Student Registration No.** | *[IT Number 3]* |
| **Owned Microservice** | **Ride Management Service** (`ride-management-service`) |
| **Service HTTP Port** | Port `8083` |
| **Assigned Database** | `ride_mgmt_db` (`rides`) |
| **Git Feature Branch** | `feature/member3-ride-orchestrator` |
| **Pull Request** | PR #3 |

---

## 2. Microservice Scope & Responsibility (LO1, LO3, I1)

**Primary Ownership:** Central workflow orchestrator: ride bookings, automated candidate discovery, state machine transitions, and trip history.

### Key Endpoints Governed:
| Endpoint | Responsibility |
| :--- | :--- |
| `POST /api/v1/rides` | Create ride booking request (REQUESTED state) |
| `POST /api/v1/rides/{rideId}/assign` | Query Driver & Vehicle Service and assign candidate driver |
| `POST /api/v1/rides/{rideId}/accept` | Driver accepts assigned ride (ACCEPTED state) |
| `POST /api/v1/rides/{rideId}/start` | Trip commences (IN_PROGRESS state) |
| `POST /api/v1/rides/{rideId}/complete` | Complete trip and trigger synchronous fare calculation |
| `POST /api/v1/rides/{rideId}/cancel` | Cancel ride request (CANCELLED state) |
| `GET /api/v1/rides?userId={userId}` | Retrieve trip history for passenger or driver |

### Explicit Service Boundaries (§6.1):
- **Data Boundary:** This service exclusively owns and manages `ride_mgmt_db`. It never queries or mutates databases owned by other microservices.
- **Interservice Contract:** Shared data references (such as `userId`, `driverId`, `rideId`) are stored as stable identifiers (UUIDs/strings), not embedded cross-service documents.

---

## 3. Technology Stack & Prerequisites

- **Java Development Kit (JDK):** Version 21 (Temurin / OpenJDK)
- **Framework:** Spring Boot 4.1.1 (Spring WebMvc, Spring Data MongoDB, Spring Security, Validation)
- **API Documentation:** SpringDoc OpenAPI 3.0 / Swagger UI
- **Database:** MongoDB (Local or MongoDB Atlas cluster database: `ride_mgmt_db`)
- **Build Tool:** Apache Maven 3.9+ (Embedded Maven Wrapper `./mvnw` provided)

---

## 4. Local Build & Test Instructions (I2)

This repository is completely isolated and self-contained. You can compile, run automated unit/integration tests, and package the service without external parent dependencies.

### Step 1: Run Automated Tests
```bash
./mvnw clean test
```
All tests should pass with `BUILD SUCCESS`.

### Step 2: Start the Microservice
```bash
./mvnw spring-boot:run -pl ride-management-service
```
*(Alternatively, `cd ride-management-service && ../mvnw spring-boot:run`)*

The service will start and listen on port **`8083`**.

### Step 3: Access Interactive Swagger UI
Once running, open your web browser at:
```text
http://localhost:8083/swagger-ui/index.html
```
Raw OpenAPI documentation is available at:
```text
http://localhost:8083/v3/api-docs
```

---

## 5. Configuration & Non-Sensitive Secrets (§6.3)

In accordance with institutional guidelines (§6.3), **no passwords, tokens, or live credentials are committed to this repository**.
- Template configuration is provided in: [`ride-management-service/.env.example`](ride-management-service/.env.example)
- Default configuration lives in: [`ride-management-service/src/main/resources/application.properties`](ride-management-service/src/main/resources/application.properties)

To configure your local or Atlas MongoDB connection string:
```bash
export MONGODB_URI="mongodb://localhost:27017/ride_mgmt_db"
export JWT_SECRET="your-32-character-minimum-hmac-sha-secret"
```

---

## 6. Git & GitHub Submission Instructions (LO4, I3)

To publish this individual microservice project to your personal GitHub repository for assessment:

### 1. Initialize Git Repository
```bash
git init
git config user.name "Your Name"
git config user.email "your.itnumber@my.sliit.lk"
```

### 2. Initial Main Branch Commit
```bash
git add .
git commit -m "feat: initial commit for Member 3 (Ride Management Service)"
git branch -M main
```

### 3. Connect to your GitHub Repository
Create a new private/public repository on GitHub (e.g., `IT3130_Member3_ride-management-service`):
```bash
git remote add origin https://github.com/<YOUR_GITHUB_USERNAME>/<REPO_NAME>.git
git push -u origin main
```

### 4. Create Feature Branch & Pull Request (for I3 Marks)
To demonstrate branch discipline and code review workflow:
```bash
git checkout -b feature/member3-ride-orchestrator
# Make your enhancements or updates
git add .
git commit -m "feat(ride-management-service): implement core business rules and validation"
git push -u origin feature/member3-ride-orchestrator
```
Then open a Pull Request on GitHub targeting `main`.

---

## 7. Folder Structure

```text
.
├── .github/workflows/ci.yml       # GitHub Actions automated build & test CI pipeline
├── .gitignore                     # Ignores build targets, credentials, and IDE cache
├── mvnw / mvnw.cmd / .mvn/        # Maven wrapper (Java 21 build tool)
├── pom.xml                        # Root Maven POM (isolated for Member 3)
├── README.md                      # This member documentation
├── ride-management-service/        # Member 3 Microservice Source Code
│   ├── pom.xml                    # Module POM definition
│   ├── .env.example               # Non-sensitive configuration template
│   ├── HELP.md                    # Spring Boot helper documentation
│   └── src/                       # Java 21 controllers, services, repositories & tests
├── docs/                          # Architecture & viva documentation
│   ├── architecture.md            # Overall system architecture diagram & rationale
│   ├── sequence-ride-booking.md   # End-to-end integration sequence diagram
│   ├── runbook/                   # Member 3 execution runbook
│   ├── viva/                      # Member 3 API testing & viva demonstration guide
│   └── report/                    # Group Technical Report reference
└── postman/                       # Postman test collection and environment
```

---

## 8. Viva & Demonstration Readiness (I4)

Please refer to:
- [`docs/runbook/member-3-ride-management-service-runbook.md`](docs/runbook/member-3-ride-management-service-runbook.md) for step-by-step local execution guides.
- [`docs/viva/member-3-ride-management-service-api-testing-and-viva-guide.md`](docs/viva/member-3-ride-management-service-api-testing-and-viva-guide.md) for sample curl commands, testing scripts, and viva question preparations.
