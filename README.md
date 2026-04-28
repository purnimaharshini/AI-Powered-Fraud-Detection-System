# Fraud Detection in Online Transaction

This project migrates the original Python Streamlit prototype into a full-stack Java application using Spring Boot, MySQL, and a frontend built with HTML, CSS, and JavaScript.

## Stack

- Java 21
- Spring Boot 3
- Spring Web + Spring Data JPA
- MySQL 8
- Vanilla HTML, CSS, and JavaScript
- Chart.js for dashboard charts

## What Changed

- Replaced the Streamlit app with a Spring Boot web application.
- Moved transaction analytics into Java services and REST APIs.
- Added MySQL persistence and CSV bootstrap import for `bank_transactions.csv`.
- Converted the scikit-learn `fraud_model.pkl` into a Java-readable random forest JSON model.
- Added a frontend dashboard for login, filters, KPIs, charts, transaction evidence, and fraud prediction.

## Authentication

- Create an account from the landing page using username, email, and password.
- Credentials are stored in MySQL in the `users` table.
- Sign-in accepts either the username or the email address.

## Run MySQL

```bash
docker compose up -d
```

The included MySQL container is exposed on `localhost:3307` to avoid conflicts with any existing local MySQL service already using `3306`.

## Run the Application

```bash
mvn spring-boot:run
```

The app will be available at `http://localhost:8080`.

## Environment Variables

You can override the default MySQL connection with:

```bash
DB_URL=jdbc:mysql://localhost:3307/fraud_detection?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USERNAME=root
DB_PASSWORD=root
APP_BOOTSTRAP_ENABLED=true
APP_BOOTSTRAP_CSV_PATH=classpath:data/bank_transactions.csv
APP_RESEND_API_KEY=re_xxxxx
APP_RESEND_API_KEY_PATH=access_token.txt
APP_RESEND_FROM_EMAIL=onboarding@resend.dev
APP_RESEND_FROM_NAME="Fraud Detection Alerts"
```

The included `access_token.txt` can be used as the Resend API key source.

Users can set a dedicated notification email from the app UI. Fraud alerts are sent to that saved profile field, not to an environment-variable recipient override.

To send fraud alerts to arbitrary recipients, replace the onboarding sender with an address on a verified domain in your Resend account.

## Project Structure

```text
src/main/java/com/frauddetection/online
src/main/resources/data/bank_transactions.csv
src/main/resources/model/fraud_model.json
src/main/resources/static
scripts/export_random_forest.py
```

## Notes About the Model

- The runtime application no longer depends on Python.
- `scripts/export_random_forest.py` exports the original scikit-learn random forest into `src/main/resources/model/fraud_model.json`.
- The Java backend loads that JSON and evaluates the same tree ensemble natively.

## Test

```bash
mvn test
```
