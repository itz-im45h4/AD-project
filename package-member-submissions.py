#!/usr/bin/env python3
"""
RideLink - Member Submissions Packager (IT3130)
Generates clean, isolated submission directories and ZIP files for each of the 4 group members.
Removes all other members' services, runbooks, and temporary/secret files.
Each generated folder is a fully self-contained Maven project ready for GitHub and Courseweb.
"""

import os
import sys
import shutil
import zipfile
import subprocess
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent
RIDELINK_DIR = PROJECT_ROOT / "ridelink"
SUBMISSIONS_DIR = PROJECT_ROOT / "submissions"
PLAN_DIR = PROJECT_ROOT / "plan"

MEMBERS = [
    {
        "num": 1,
        "name": "Member 1",
        "service_dir": "account-service",
        "service_title": "Account & Identity Service",
        "package_name": "IT3130_Member1_AccountService",
        "port": 8081,
        "db": "account_db",
        "collections": ["users"],
        "branch": "feature/member1-account-jwt",
        "pr": "#1",
        "runbook": "member-1-account-service-runbook.md",
        "viva_guide": "member-1-account-service-api-testing-and-viva-guide.md",
        "scope": "User identity, credentials, role-based access control (RBAC), authentication token lifecycles.",
        "endpoints": [
            ("POST", "/api/v1/accounts/register", "Passenger and driver account registration"),
            ("POST", "/api/v1/auth/login", "User login and JWT token issuance"),
            ("GET", "/api/v1/accounts/me", "Get current user profile (excludes password)"),
            ("PUT", "/api/v1/accounts/me", "Update current user profile"),
            ("PATCH", "/api/v1/accounts/{accountId}/status", "Update account status (ACTIVE, SUSPENDED, DEACTIVATED)")
        ]
    },
    {
        "num": 2,
        "name": "Member 2",
        "service_dir": "driver-vehicle-service",
        "service_title": "Driver & Vehicle Service",
        "package_name": "IT3130_Member2_DriverVehicleService",
        "port": 8082,
        "db": "driver_vehicle_db",
        "collections": ["drivers", "vehicles"],
        "branch": "feature/member2-driver-location",
        "pr": "#2",
        "runbook": "member-2-driver-vehicle-service-runbook.md",
        "viva_guide": "member-2-driver-vehicle-service-api-testing-and-viva-guide.md",
        "scope": "Driver operational readiness, vehicle technical specifications, availability status, simulated location telemetry, and eligible driver queries.",
        "endpoints": [
            ("POST", "/api/v1/drivers/profile", "Create driver operational profile and vehicle details"),
            ("GET", "/api/v1/drivers/{driverId}", "Retrieve driver operational profile and vehicle details"),
            ("PUT", "/api/v1/drivers/{driverId}/availability", "Update availability status (ONLINE, OFFLINE, ON_TRIP)"),
            ("PUT", "/api/v1/drivers/{driverId}/location", "Update simulated GPS coordinates and operational zone"),
            ("GET", "/api/v1/drivers/available?zone={zone}", "Query available, online candidate drivers for Ride Management")
        ]
    },
    {
        "num": 3,
        "name": "Member 3",
        "service_dir": "ride-management-service",
        "service_title": "Ride Management Service",
        "package_name": "IT3130_Member3_RideManagementService",
        "port": 8083,
        "db": "ride_mgmt_db",
        "collections": ["rides"],
        "branch": "feature/member3-ride-orchestrator",
        "pr": "#3",
        "runbook": "member-3-ride-management-service-runbook.md",
        "viva_guide": "member-3-ride-management-service-api-testing-and-viva-guide.md",
        "scope": "Central workflow orchestrator: ride bookings, automated candidate discovery, state machine transitions, and trip history.",
        "endpoints": [
            ("POST", "/api/v1/rides", "Create ride booking request (REQUESTED state)"),
            ("POST", "/api/v1/rides/{rideId}/assign", "Query Driver & Vehicle Service and assign candidate driver"),
            ("POST", "/api/v1/rides/{rideId}/accept", "Driver accepts assigned ride (ACCEPTED state)"),
            ("POST", "/api/v1/rides/{rideId}/start", "Trip commences (IN_PROGRESS state)"),
            ("POST", "/api/v1/rides/{rideId}/complete", "Complete trip and trigger synchronous fare calculation"),
            ("POST", "/api/v1/rides/{rideId}/cancel", "Cancel ride request (CANCELLED state)"),
            ("GET", "/api/v1/rides?userId={userId}", "Retrieve trip history for passenger or driver")
        ]
    },
    {
        "num": 4,
        "name": "Member 4",
        "service_dir": "fare-payment-service",
        "service_title": "Fare & Payment Service",
        "package_name": "IT3130_Member4_FarePaymentService",
        "port": 8084,
        "db": "fare_payment_db",
        "collections": ["fares", "payments", "receipts"],
        "branch": "feature/member4-fare-amqp",
        "pr": "#4",
        "runbook": "member-4-fare-payment-service-runbook.md",
        "viva_guide": "member-4-fare-payment-service-api-testing-and-viva-guide.md",
        "scope": "Financial calculations, upfront trip estimates, final fare calculations, simulated payment settlements, immutable receipts, and AMQP event publishing.",
        "endpoints": [
            ("POST", "/api/v1/fares/estimate", "Calculate upfront fare estimate from distance and duration"),
            ("POST", "/api/v1/fares/finalize", "Synchronous final fare calculation called upon trip completion"),
            ("GET", "/api/v1/fares/{rideId}", "Retrieve finalized fare calculation by rideId"),
            ("POST", "/api/v1/payments", "Record simulated payment and publish PaymentRecorded event to CloudAMQP"),
            ("GET", "/api/v1/receipts/{rideId}", "Retrieve immutable payment receipt for rideId")
        ]
    }
]

EXCLUDE_PATTERNS = [
    "target",
    "build",
    ".idea",
    ".vscode",
    "*.iml",
    "*.ipr",
    "*.iws",
    "*.log",
    "application-local.properties",
    "application-local.yml",
    "application-local.yaml",
    ".env",
    ".env.*"
]

def generate_member_pom(parent_pom_path: Path, member: dict) -> str:
    original = parent_pom_path.read_text(encoding="utf-8")
    
    # Replace the <modules> block with only this member's module
    modules_start = original.find("<modules>")
    modules_end = original.find("</modules>") + len("</modules>")
    if modules_start == -1 or modules_end == -1:
        raise ValueError("Could not find <modules> block in parent pom.xml")
    
    single_module = f"""<modules>
        <module>{member['service_dir']}</module>
    </modules>"""
    
    new_pom = original[:modules_start] + single_module + original[modules_end:]
    
    # Update <name> and <description> (XML-escaped)
    title_xml = member['service_title'].replace("&", "&amp;")
    new_pom = new_pom.replace(
        "<name>RideLink</name>",
        f"<name>RideLink - {title_xml}</name>"
    )
    new_pom = new_pom.replace(
        "<description>RideLink backend microservices reactor</description>",
        f"<description>RideLink {title_xml} (Member {member['num']} Submission)</description>"
    )
    return new_pom

def generate_member_readme(member: dict) -> str:
    endpoint_rows = "\n".join(
        f"| `{method} {path}` | {desc} |"
        for method, path, desc in member["endpoints"]
    )
    
    collections_list = ", ".join(f"`{c}`" for c in member["collections"])
    
    readme = f"""# RideLink — {member['service_title']}
### IT3130: Application Development — Individual Submission (Member {member['num']})

---

## 1. Submission & Ownership Metadata

| Field | Details |
| :--- | :--- |
| **Institution** | Sri Lanka Institute of Information Technology (SLIIT) |
| **Faculty / Department** | Faculty of Computing — Department of Information Technology |
| **Module** | IT3130 — Application Development (Year 3, Semester 1) |
| **Assessment** | Group Assignment & Individual Microservice Ownership |
| **Assigned Member** | **Member {member['num']}** |
| **Student Full Name** | *[Student {member['num']} Full Name]* |
| **Student Registration No.** | *[IT Number {member['num']}]* |
| **Owned Microservice** | **{member['service_title']}** (`{member['service_dir']}`) |
| **Service HTTP Port** | Port `{member['port']}` |
| **Assigned Database** | `{member['db']}` ({collections_list}) |
| **Git Feature Branch** | `{member['branch']}` |
| **Pull Request** | PR {member['pr']} |

---

## 2. Microservice Scope & Responsibility (LO1, LO3, I1)

**Primary Ownership:** {member['scope']}

### Key Endpoints Governed:
| Endpoint | Responsibility |
| :--- | :--- |
{endpoint_rows}

### Explicit Service Boundaries (§6.1):
- **Data Boundary:** This service exclusively owns and manages `{member['db']}`. It never queries or mutates databases owned by other microservices.
- **Interservice Contract:** Shared data references (such as `userId`, `driverId`, `rideId`) are stored as stable identifiers (UUIDs/strings), not embedded cross-service documents.

---

## 3. Technology Stack & Prerequisites

- **Java Development Kit (JDK):** Version 21 (Temurin / OpenJDK)
- **Framework:** Spring Boot 4.1.1 (Spring WebMvc, Spring Data MongoDB, Spring Security, Validation)
- **API Documentation:** SpringDoc OpenAPI 3.0 / Swagger UI
- **Database:** MongoDB (Local or MongoDB Atlas cluster database: `{member['db']}`)
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
./mvnw spring-boot:run -pl {member['service_dir']}
```
*(Alternatively, `cd {member['service_dir']} && ../mvnw spring-boot:run`)*

The service will start and listen on port **`{member['port']}`**.

### Step 3: Access Interactive Swagger UI
Once running, open your web browser at:
```text
http://localhost:{member['port']}/swagger-ui/index.html
```
Raw OpenAPI documentation is available at:
```text
http://localhost:{member['port']}/v3/api-docs
```

---

## 5. Configuration & Non-Sensitive Secrets (§6.3)

In accordance with institutional guidelines (§6.3), **no passwords, tokens, or live credentials are committed to this repository**.
- Template configuration is provided in: [`{member['service_dir']}/.env.example`]({member['service_dir']}/.env.example)
- Default configuration lives in: [`{member['service_dir']}/src/main/resources/application.properties`]({member['service_dir']}/src/main/resources/application.properties)

To configure your local or Atlas MongoDB connection string:
```bash
export MONGODB_URI="mongodb://localhost:27017/{member['db']}"
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
git commit -m "feat: initial commit for Member {member['num']} ({member['service_title']})"
git branch -M main
```

### 3. Connect to your GitHub Repository
Create a new private/public repository on GitHub (e.g., `IT3130_Member{member['num']}_{member['service_dir']}`):
```bash
git remote add origin https://github.com/<YOUR_GITHUB_USERNAME>/<REPO_NAME>.git
git push -u origin main
```

### 4. Create Feature Branch & Pull Request (for I3 Marks)
To demonstrate branch discipline and code review workflow:
```bash
git checkout -b {member['branch']}
# Make your enhancements or updates
git add .
git commit -m "feat({member['service_dir']}): implement core business rules and validation"
git push -u origin {member['branch']}
```
Then open a Pull Request on GitHub targeting `main`.

---

## 7. Folder Structure

```text
.
├── .github/workflows/ci.yml       # GitHub Actions automated build & test CI pipeline
├── .gitignore                     # Ignores build targets, credentials, and IDE cache
├── mvnw / mvnw.cmd / .mvn/        # Maven wrapper (Java 21 build tool)
├── pom.xml                        # Root Maven POM (isolated for Member {member['num']})
├── README.md                      # This member documentation
├── {member['service_dir']}/        # Member {member['num']} Microservice Source Code
│   ├── pom.xml                    # Module POM definition
│   ├── .env.example               # Non-sensitive configuration template
│   ├── HELP.md                    # Spring Boot helper documentation
│   └── src/                       # Java 21 controllers, services, repositories & tests
├── docs/                          # Architecture & viva documentation
│   ├── architecture.md            # Overall system architecture diagram & rationale
│   ├── sequence-ride-booking.md   # End-to-end integration sequence diagram
│   ├── runbook/                   # Member {member['num']} execution runbook
│   ├── viva/                      # Member {member['num']} API testing & viva demonstration guide
│   └── report/                    # Group Technical Report reference
└── postman/                       # Postman test collection and environment
```

---

## 8. Viva & Demonstration Readiness (I4)

Please refer to:
- [`docs/runbook/{member['runbook']}`](docs/runbook/{member['runbook']}) for step-by-step local execution guides.
- [`docs/viva/{member['viva_guide']}`](docs/viva/{member['viva_guide']}) for sample curl commands, testing scripts, and viva question preparations.
"""
    return readme

def generate_ci_workflow(member: dict) -> str:
    return f"""name: Verify {member['service_title']}

on:
  pull_request:
    branches: [main, develop, master]
  push:
    branches: [main, develop, master]

permissions:
  contents: read

jobs:
  verify:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Code
        uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven

      - name: Verify and Test Service
        run: ./mvnw --batch-mode verify
"""

def generate_git_init_script(member: dict) -> str:
    return f"""#!/usr/bin/env bash
set -euo pipefail

# Quick-start script to initialize git and prepare for GitHub push
echo "==> Initializing Git repository for Member {member['num']}: {member['service_title']}..."

if [ -d ".git" ]; then
    echo "Git repository is already initialized."
else
    git init
    git branch -M main
    git add .
    git commit -m "feat: initial commit for Member {member['num']} {member['service_title']}"
    echo "==> Repository initialized and initial commit created on 'main' branch."
fi

echo ""
echo "Next Steps:"
echo "1. Create a repository on GitHub (e.g., https://github.com/your-username/{member['package_name']})"
echo "2. Run: git remote add origin https://github.com/<your-username>/{member['package_name']}.git"
echo "3. Run: git push -u origin main"
echo "4. Create feature branch: git checkout -b {member['branch']}"
"""

def copy_service_clean(src_dir: Path, dest_dir: Path):
    """Copies service directory excluding target and credential files."""
    def ignore_filter(directory, files):
        ignored = []
        for f in files:
            if f in ("target", "build", ".idea", ".vscode"):
                ignored.append(f)
            elif f.startswith("application-local."):
                ignored.append(f)
            elif f == ".env" or (f.startswith(".env.") and f != ".env.example"):
                ignored.append(f)
            elif f.endswith(".log") or f.endswith(".iml"):
                ignored.append(f)
        return ignored

    shutil.copytree(src_dir, dest_dir, ignore=ignore_filter)

def create_zip_archive(source_dir: Path, zip_path: Path):
    """Creates a standard zip archive from source_dir."""
    if zip_path.exists():
        zip_path.unlink()
    
    with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zf:
        for root, dirs, files in os.walk(source_dir):
            # Sort for deterministic archive
            dirs.sort()
            files.sort()
            for file in files:
                full_path = Path(root) / file
                rel_path = full_path.relative_to(source_dir)
                zf.write(full_path, rel_path)

def package_member(member: dict, verify: bool = True):
    print(f"\n==================================================")
    print(f" Packaging Member {member['num']}: {member['service_title']}")
    print(f"==================================================")
    
    target_dir = SUBMISSIONS_DIR / member["package_name"]
    zip_file = SUBMISSIONS_DIR / f"{member['package_name']}.zip"
    
    # 1. Clean destination
    if target_dir.exists():
        shutil.rmtree(target_dir)
    target_dir.mkdir(parents=True, exist_ok=True)
    
    # 2. Copy Maven Wrapper files
    shutil.copytree(RIDELINK_DIR / ".mvn", target_dir / ".mvn")
    shutil.copy(RIDELINK_DIR / "mvnw", target_dir / "mvnw")
    shutil.copy(RIDELINK_DIR / "mvnw.cmd", target_dir / "mvnw.cmd")
    os.chmod(target_dir / "mvnw", 0o755)
    
    # 3. Create isolated root pom.xml
    single_pom = generate_member_pom(RIDELINK_DIR / "pom.xml", member)
    (target_dir / "pom.xml").write_text(single_pom, encoding="utf-8")
    
    # 4. Copy isolated service directory
    src_service_dir = RIDELINK_DIR / member["service_dir"]
    copy_service_clean(src_service_dir, target_dir / member["service_dir"])
    
    # 5. Create .gitignore
    gitignore_content = """# Secrets and local credentials
.env
.env.*
!.env.example
application-local.properties
application-local.yml
application-local.yaml

# Build output
target/
**/target/
build/
**/build/
*.log
coverage/

# IDE and Editor files
.idea/
.vscode/
*.iml
*.ipr
*.iws
.classpath
.project
.settings/
.DS_Store
Thumbs.db
"""
    (target_dir / ".gitignore").write_text(gitignore_content, encoding="utf-8")
    
    # 6. Copy documentation
    docs_dest = target_dir / "docs"
    docs_dest.mkdir(parents=True, exist_ok=True)
    
    # Architecture and sequence
    if (RIDELINK_DIR / "docs" / "architecture.md").exists():
        shutil.copy(RIDELINK_DIR / "docs" / "architecture.md", docs_dest / "architecture.md")
    if (RIDELINK_DIR / "docs" / "sequence-ride-booking.md").exists():
        shutil.copy(RIDELINK_DIR / "docs" / "sequence-ride-booking.md", docs_dest / "sequence-ride-booking.md")
    
    # Contracts
    if (RIDELINK_DIR / "docs" / "contracts").exists():
        shutil.copytree(RIDELINK_DIR / "docs" / "contracts", docs_dest / "contracts")
        
    # Decisions (ADRs)
    if (RIDELINK_DIR / "docs" / "decisions").exists():
        shutil.copytree(RIDELINK_DIR / "docs" / "decisions", docs_dest / "decisions")
        
    # Testing plan
    if (RIDELINK_DIR / "docs" / "testing").exists():
        shutil.copytree(RIDELINK_DIR / "docs" / "testing", docs_dest / "testing")
        
    # Technical report reference
    if (RIDELINK_DIR / "docs" / "report").exists():
        shutil.copytree(RIDELINK_DIR / "docs" / "report", docs_dest / "report")
        
    # Copy ONLY this member's runbook and viva guide
    runbook_dest = docs_dest / "runbook"
    runbook_dest.mkdir(parents=True, exist_ok=True)
    src_runbook = PLAN_DIR / member["runbook"]
    if src_runbook.exists():
        shutil.copy(src_runbook, runbook_dest / member["runbook"])
        
    viva_dest = docs_dest / "viva"
    viva_dest.mkdir(parents=True, exist_ok=True)
    src_viva = PLAN_DIR / member["viva_guide"]
    if src_viva.exists():
        shutil.copy(src_viva, viva_dest / member["viva_guide"])
        
    # 7. Copy Postman collection and environment
    if (RIDELINK_DIR / "postman").exists():
        shutil.copytree(RIDELINK_DIR / "postman", target_dir / "postman")
        
    # 8. GitHub Actions CI workflow
    workflow_dir = target_dir / ".github" / "workflows"
    workflow_dir.mkdir(parents=True, exist_ok=True)
    (workflow_dir / "ci.yml").write_text(generate_ci_workflow(member), encoding="utf-8")
    
    # 9. Individual tailored README.md
    (target_dir / "README.md").write_text(generate_member_readme(member), encoding="utf-8")
    
    # 10. Helper git init script
    git_script = target_dir / "init-git-repo.sh"
    git_script.write_text(generate_git_init_script(member), encoding="utf-8")
    os.chmod(git_script, 0o755)
    
    # 11. Create ZIP Archive
    create_zip_archive(target_dir, zip_file)
    zip_size_kb = zip_file.stat().st_size / 1024
    print(f" -> Created directory: {target_dir.relative_to(PROJECT_ROOT)}")
    print(f" -> Created ZIP file : {zip_file.relative_to(PROJECT_ROOT)} ({zip_size_kb:.1f} KB)")
    
    # 12. Verification check
    if verify:
        print(f" -> Verifying standalone build with './mvnw test-compile'...")
        res = subprocess.run(
            ["./mvnw", "test-compile", "--batch-mode"],
            cwd=target_dir,
            capture_output=True,
            text=True
        )
        if res.returncode == 0:
            print(f" -> [PASS] Member {member['num']} builds and compiles cleanly in isolation!")
            # Clean build directory after verification to leave folder pristine
            subprocess.run(
                ["./mvnw", "clean", "--batch-mode"],
                cwd=target_dir,
                capture_output=True
            )
        else:
            print(f" -> [FAIL] Build verification failed for Member {member['num']}!")
            print(res.stderr[-400:])
            raise RuntimeError(f"Build failed for {member['package_name']}")

def generate_master_submission_readme():
    content = r"""# IT3130 RideLink — Group Member Submission Packages

This directory contains separated, self-contained project folders and submission ZIP archives for each group member.
In accordance with assignment brief §6.1, §6.4, and §7:
- All other members' services, runbooks, and temporary files have been **completely removed** from each package.
- Each package contains its own isolated Maven reactor `pom.xml`, Maven wrapper (`./mvnw`), `.gitignore`, Postman collection, architectural documents, and the member's dedicated runbook and viva guide.
- **No secrets, database credentials, or local passwords are included.**

---

## 1. Generated Packages Summary

| Member | Assigned Service | Directory | ZIP Package | Port & Database |
| :---: | :--- | :--- | :--- | :--- |
| **Member 1** | **Account & Identity Service** | [`IT3130_Member1_AccountService/`](IT3130_Member1_AccountService/) | `IT3130_Member1_AccountService.zip` | Port `8081` \| `account_db` |
| **Member 2** | **Driver & Vehicle Service** | [`IT3130_Member2_DriverVehicleService/`](IT3130_Member2_DriverVehicleService/) | `IT3130_Member2_DriverVehicleService.zip` | Port `8082` \| `driver_vehicle_db` |
| **Member 3** | **Ride Management Service** | [`IT3130_Member3_RideManagementService/`](IT3130_Member3_RideManagementService/) | `IT3130_Member3_RideManagementService.zip` | Port `8083` \| `ride_mgmt_db` |
| **Member 4** | **Fare & Payment Service** | [`IT3130_Member4_FarePaymentService/`](IT3130_Member4_FarePaymentService/) | `IT3130_Member4_FarePaymentService.zip` | Port `8084` \| `fare_payment_db` |

---

## 2. How Each Member Submits to GitHub

Each member can push their isolated microservice to their own GitHub repository in 4 simple commands:

```bash
# 1. Navigate to your member folder
cd IT3130_MemberX_...

# 2. Run the included helper script (or git init manually)
./init-git-repo.sh

# 3. Connect to your personal GitHub repository
git remote add origin https://github.com/<your-username>/<your-repo-name>.git
git push -u origin main

# 4. Create feature branch and pull request for I3 marks
git checkout -b feature/memberX-...
git push -u origin feature/memberX-...
```

---

## 3. How to Submit ZIP to Courseweb

If required by your lecturer to submit an individual ZIP file:
- Simply upload the corresponding `.zip` file from this folder:
  - Member 1: `IT3130_Member1_AccountService.zip`
  - Member 2: `IT3130_Member2_DriverVehicleService.zip`
  - Member 3: `IT3130_Member3_RideManagementService.zip`
  - Member 4: `IT3130_Member4_FarePaymentService.zip`

Each ZIP extracts into a buildable Maven project that can immediately be verified with `./mvnw clean test`.
"""
    (SUBMISSIONS_DIR / "README.md").write_text(content, encoding="utf-8")

def main():
    import argparse
    parser = argparse.ArgumentParser(description="Package separated member submissions for RideLink (IT3130)")
    parser.add_argument("--member", "-m", type=int, choices=[1, 2, 3, 4], help="Package only a specific member (1-4)")
    parser.add_argument("--no-verify", action="store_true", help="Skip Maven compilation verification")
    args = parser.parse_args()

    SUBMISSIONS_DIR.mkdir(parents=True, exist_ok=True)
    
    print("\nStarting generation of separated member submission packages...")
    selected_members = [m for m in MEMBERS if args.member is None or m["num"] == args.member]
    for member in selected_members:
        package_member(member, verify=not args.no_verify)
        
    generate_master_submission_readme()
    print("\n==================================================")
    print(" ALL SELECTED PACKAGES GENERATED AND VERIFIED!")
    print(f" Output Location: {SUBMISSIONS_DIR.relative_to(PROJECT_ROOT)}")
    print("==================================================\n")

if __name__ == "__main__":
    main()
