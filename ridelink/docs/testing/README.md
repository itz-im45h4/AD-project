# Testing Evidence

Keep repeatable evidence for the assignment here. Do not commit generated build output, secrets, or real customer data.

## Required evidence

- Unit tests for each service, using JUnit 5 and Mockito.
- Integration tests for MongoDB and RabbitMQ using Testcontainers.
- Postman happy-path workflow covering registration, driver readiness, ride assignment/lifecycle, fare, payment, and receipt.
- At least two negative workflows: for example no available driver, invalid state transition, invalid input, unauthorized access, or failed payment.

## Suggested layout

```text
testing/
├── README.md
├── test-plan.md                 # scenarios and expected outcomes
└── evidence/                    # concise exported results/screenshots for submission
```

Create `test-plan.md` before integration work begins and add evidence only when a workflow is demonstrably passing.
