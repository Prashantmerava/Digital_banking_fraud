# Digital Banking System (no Docker, Kafka, Redis, Payment, or Notification Service)

This project contains four Spring Boot services:

- `api-gateway` — public entry point, port 8080
- `account-service` — account creation, balance, debit/credit, account blocking, port 8081
- `transaction-service` — transfers, history, OTP verification, port 8082
- `fraud-detection-service` — internal fraud checks, port 8084

Notification Service and its client have been removed. Since no notification channel remains to deliver OTPs, suspicious transfers are cancelled, flagged, and refunded instead of being left waiting for an unreachable OTP. Payment Service, Kafka, Redis, Docker, and Docker Compose are not required.

## Requirements

- JDK 17
- Maven 3.8+
- MySQL Server running locally on port 3306

The Account Service uses database `account_db`; Transaction Service uses `transaction_db`. For local development, the defaults in `application.yml` are `root` / `root`. Override them with environment variables if your MySQL credentials differ. Do not commit production credentials.

## Build

From this directory (`banking`), build each service separately:

```bash
cd account-service && mvn clean package
cd ../fraud-detection-service && mvn clean package
cd ../transaction-service && mvn clean package
cd ../api-gateway && mvn clean package
```

## Run locally

Start MySQL first. Then open four separate terminal windows, each starting from the `banking` directory.

Terminal 1:
```bash
cd account-service
mvn spring-boot:run
```

Terminal 2:
```bash
cd fraud-detection-service
mvn spring-boot:run
```

Terminal 3:
```bash
cd transaction-service
mvn spring-boot:run
```

Terminal 4:
```bash
cd api-gateway
mvn spring-boot:run
```

Start the account service and fraud service before transaction service. Start the gateway after the backend services.

## Swagger UI

With all services running, open:

- Gateway Swagger UI: http://localhost:8080/swagger-ui.html
- Account Service Swagger UI directly: http://localhost:8081/swagger-ui.html
- Transaction Service Swagger UI directly: http://localhost:8082/swagger-ui.html
- Fraud Detection Swagger UI directly (internal API): http://localhost:8084/swagger-ui.html

The Gateway Swagger UI aggregates the public Account and Transaction API specifications. The fraud-check endpoint is an internal service endpoint and is intentionally not exposed through the public gateway.

## Gateway routes

- `/api/v1/accounts/**` -> Account Service
- `/api/v1/transactions/**` -> Transaction Service
- `/account-service/v3/api-docs` -> Account Service OpenAPI document for Swagger UI
- `/transaction-service/v3/api-docs` -> Transaction Service OpenAPI document for Swagger UI

## Database configuration

For local MySQL, defaults are configured in each service's `application.yml`. For hosted deployment, set these variables on the corresponding Railway service:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

For Transaction Service, also set:

- `ACCOUNT_SERVICE_URL`
- `FRAUD_SERVICE_URL`

For Fraud Detection Service, set:

- `ACCOUNT_SERVICE_URL`

For API Gateway, set:

- `ACCOUNT_SERVICE_URL`
- `TRANSACTION_SERVICE_URL`

Set each URL to the actual internal service URL provided by your hosting platform. Do not use `localhost` for calls between separately deployed services.

## Important limitations

- Fraud detection is an internal API and should not be exposed publicly without appropriate authentication/authorization.
- The rate limiter is in-memory and resets when the gateway restarts; it is not shared across instances.
- This repository should be built and tested locally before deployment. Database operations and cross-service transfer behavior must be verified with a running MySQL instance.


## Security warning before public deployment

This project does not currently implement user authentication/authorization for account and transaction APIs. Treat it as a portfolio demo only, use fake data, and do not expose real banking/customer information. Before making write endpoints publicly accessible, add and test authentication/authorization and review the transaction saga for failure/retry behavior.
