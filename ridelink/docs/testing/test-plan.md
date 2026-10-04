# Repeatable Test Plan

Run the shared Postman collection with a running MongoDB and RabbitMQ instance. Save the collection-run export or screenshots in `evidence/` before submission.

| ID | Scenario | Expected result |
| --- | --- | --- |
| HP-1 | Register passenger and driver; login both | 201 accounts and bearer tokens with correct roles |
| HP-2 | Driver creates profile, updates location, sets ONLINE | Driver is returned by the eligible-driver lookup for that zone |
| HP-3 | Passenger creates and assigns ride; driver accepts, starts and completes it | Valid state transitions and a final LKR fare |
| HP-4 | Record simulated payment and retrieve receipt | Payment is stored; `PaymentRecorded` updates Ride payment status asynchronously |
| HP-5 | Query driver operational profile by ID (`GET /drivers/{id}`) | 200 OK with driver profile and vehicle specifications |
| HP-6 | Query finalized fare by ride ID (`GET /fares/{rideId}`) | 200 OK with binding fare record in PENDING payment status |
| N-1 | Assign a ride where no ONLINE driver exists | 409 Problem Detail |
| N-2 | Start a ride before acceptance | 409 Problem Detail |
| N-3 | Call a protected endpoint with no/invalid bearer token | 401/403 |
| N-4 | Submit payment method `FAIL` | Stored FAILED payment and corresponding event/receipt |
