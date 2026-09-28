# ROYAL EVENTS — 5-Language Event Material & Booking Manager

This package is a GitHub-ready version of the supplied **ROYAL EVENTS — Supply Manifest & Material Planner**.

## Five languages used

1. **HTML** — application structure and forms (`frontend/index.html`)
2. **CSS** — responsive visual design (`frontend/styles.css`)
3. **JavaScript** — login, event checklist, quantities, search, UPI launch, export and browser storage (`frontend/app.js`)
4. **Java** — Spring Boot REST backend (`backend/src/main/java/...`)
5. **SQL** — H2 database schema for booking storage (`backend/src/main/resources/schema.sql`)

The original project already defines the event categories, event types, material master list, login screen, booking/contact form, payment section, checklist controls and export behavior; those are retained in this version.

## Project structure

```text
royal-events-5-language/
├── frontend/
│   ├── index.html
│   ├── styles.css
│   ├── app.js
│   └── original-reference.html
├── backend/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/royalevents/
│       │   ├── RoyalEventsApplication.java
│       │   ├── controller/BookingController.java
│       │   ├── model/Booking.java
│       │   └── repository/BookingRepository.java
│       └── resources/
│           ├── static/       # frontend served by Spring Boot
│           ├── application.properties
│           └── schema.sql
└── sql/
    └── schema.sql
```

## Run with Spring Boot

Requirements: **JDK 17+** and **Maven 3.9+**.

```bash
cd backend
mvn spring-boot:run
```

Then open:

```text
http://localhost:8080/
```

The booking form saves locally and also sends booking data to:

```text
POST /api/bookings
GET  /api/bookings
```

H2 data is stored under `backend/data/` when the application runs.

## Run frontend only

Open `frontend/index.html` in a browser. The supplied application's localStorage mode continues to work without the Java backend. Backend booking persistence requires serving the frontend through Spring Boot.

## GitHub

Upload the entire `royal-events-5-language` folder to a GitHub repository. Do not upload passwords or production payment credentials. The included login is still a lightweight demo login, matching the supplied application's client-side approach.
