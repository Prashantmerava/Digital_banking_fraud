# Review notes for this archive

## Changes made
- Removed `notification-service/`.
- Removed `NotificationServiceClient` and all transaction-service notification calls/configuration.
- Removed the OTP verification workflow because there is no remaining channel to deliver OTPs. Suspicious transactions are now flagged and the sender debit is compensated/refunded.
- Removed unused transaction event classes and the unused payment transaction type.
- Added Springdoc OpenAPI UI dependencies to the API Gateway and the three backend services.
- Configured the API Gateway Swagger UI to load Account Service and Transaction Service API specifications through gateway routes.
- Added `PORT` environment-variable support to each service.
- Added same-account transfer rejection and positive-amount validation in account debit/credit methods.
- Updated local run and deployment documentation; normalized `.gitignore`.

## Static checks completed
- All four Maven POM files parse as XML.
- All four `application.yml` files parse as YAML.
- Java compiler parser accepted all 28 Java source files with zero syntax errors.
- No remaining source references to the removed notification client, OTP fields, Kafka listeners/templates, Redis templates, or removed event classes were found.
- No missing imports to project-local `com.banking.*` classes were found.

## Not verified yet
- Maven was not available in the inspection environment, so dependencies were not resolved and `mvn clean package` was not run.
- Services were not started against a live MySQL server.
- Swagger UI and gateway routes were not runtime-tested.
- End-to-end transfers and compensation behavior were not tested.

Run the local verification commands in `README-RUN-LOCAL.md` before deployment. Do not use real banking/customer data; account and transaction endpoints do not currently enforce authentication/authorization.
