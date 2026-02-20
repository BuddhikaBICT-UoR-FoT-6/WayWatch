# CeylonQueueBusPulse

An end‑to‑end **traffic + transit pulse** app:

- **Android app (Kotlin + Jetpack Compose)**: login/register, then a map screen that visualizes route geometry and aggregated traffic severity.
- **Backend (Node.js + TypeScript + Express)**: JWT auth, aggregation jobs, and integrations (TomTom Traffic APIs, OpenRouteService for route geometry generation).
- **MongoDB**: persistence for users, reports, and aggregates.
- **Redis (optional)**: caching/rate limiting.

> This repo is set up so Android builds/tests can run without exposing secrets. **Do not commit `.env`**.

---

## Monorepo layout

```
app/       # Android application
server/    # Node/TS backend (Express)
Docs/      # Documentation
```

---

## Backend API (current routes)

Base path: `/api/v1`

### Health
- `GET /health` → `{ ok: true }`

### Auth
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`

> Auth uses **JWT access tokens** + **refresh tokens** (stored server-side with rotation support). Make sure `JWT_SECRET` (and in production, `JWT_REFRESH_SECRET`) are set.

### Traffic
- `GET /api/v1/traffic?lat=<lat>&lon=<lon>`
- `POST /api/v1/traffic/bbox` with body `{ north, west, south, east, maxPoints? }`
- `GET /api/v1/routes/:routeId/points?maxPoints=12&windowStartMs=<ms?>`

### Search (TomTom)
- `GET /api/v1/search?q=<text>`
- `GET /api/v1/reverse?lat=<lat>&lon=<lon>`

---

## Prerequisites

### Android
- Android Studio (recommended)
- JDK 17
- Android SDK / emulator

### Backend
- Node.js 18+ (20+ recommended)
- npm

### Accounts / Keys (optional)
- **MongoDB Atlas** connection string
- **TomTom API key** (Traffic / Search / Reverse geocoding)
- **Upstash Redis URL** (optional)
- **OpenRouteService (ORS) API key** (optional; only for generating GeoJSON route lines)

---

## Environment variables

### Backend (`server/.env`)
Create `server/.env` (example keys):

- `MONGODB_URI` – MongoDB connection string
- `PORT` – default `3000`
- `JWT_SECRET` – access token secret
- `JWT_EXPIRES_IN` – e.g. `7d`
- `JWT_REFRESH_SECRET` – refresh token secret (required in production)
- `JWT_REFRESH_EXPIRES_IN` – e.g. `30d`
- `REDIS_URL` – optional (Upstash / Redis)

Provider integrations:
- `TOMTOM_API_KEY` – TomTom Web API key
- `ORS_API_KEY` – ORS token (only used by the route GeoJSON generator script)

> Security: keep `.env` local only. It’s already ignored by git (see `.gitignore`).

### Android (Gradle properties)
Android reads configuration via **Gradle properties** (so we don’t hardcode secrets in code):

- `MONGO_API_BASE_URL` (default: `http://10.0.2.2:3000/` for emulator)
- `TOMTOM_API_KEY` (used by some client features / manifest placeholder)

Where to set:
- `local.properties` (recommended for local dev)
- or `~/.gradle/gradle.properties`

Example (`local.properties`):

```
MONGO_API_BASE_URL=http\://10.0.2.2\:3000/
TOMTOM_API_KEY=YOUR_KEY
```

---

## Running the backend

From `server/`:

```powershell
npm install
npm run dev
```

Build + run production:

```powershell
npm run build
npm start
```

### Common pitfall: `Missing JWT_SECRET`
If you see `Missing JWT_SECRET`, it usually means the process didn’t load your `.env`.

Fixes:
- Ensure `server/.env` exists.
- Run from the `server/` directory.
- Confirm your entrypoint loads env early (this repo does `require('dotenv').config()` at the top of `server/src/index.ts`).

---

## Running the Android app

### In Android Studio
Open the repository root in Android Studio and run the `app` configuration.

### CLI

```powershell
./gradlew :app:assembleDebug
```

Run unit tests:

```powershell
./gradlew :app:testDebugUnitTest
```

---

## “Map after login” behavior

The intended flow:
1. User logs in / registers
2. App navigates to the **map screen** (route overlay + aggregated traffic)

If you’re adjusting navigation, the simplest contract is:
- Auth screens are the entrypoint when not logged in
- After auth success -> open `MapComposeActivity` (or equivalent)

---

## Aggregation pipeline (high-level)

**Goal:** unify multiple sources into a single “Location → Traffic” experience.

1. **User selects a location**
   - map tap
   - search result
   - predefined route selection
2. **Backend fetches / computes**
   - TomTom traffic flow/incidents (server-side; API key stays on the server)
   - user-submitted reports from MongoDB
3. **Backend returns normalized aggregates**
   - a stable severity scale (e.g., 0–5)
   - metadata (route/location label, time window)
4. **Android renders**
   - severity colors / markers
   - detail screen for a single report

---

## Route geometry (static GeoJSON assets)

The Android app can render route lines from:

`app/src/main/assets/routes/route_<id>.geojson`

### Validate GeoJSON

```powershell
cd server
npm run geojson:validate -- --file "..\app\src\main\assets\routes\route_138.geojson"
```

### Generate GeoJSON from OpenRouteService (ORS)

```powershell
cd server
npm run geojson:ors -- --routeId 138 --profile driving-car --coords "79.8612,6.9271;79.8725,6.9310;79.8840,6.9402;79.9000,6.9500" --out "..\app\src\main\assets\routes\route_138.geojson"
```

Notes:
- `coords` format is `lon,lat;lon,lat;...` (at least 2 points).
- Requires `ORS_API_KEY` in `server/.env`.

---

## Deployment (Render)

Yes — the backend can be hosted on **Render** while using **MongoDB Atlas**.

### Render: backend
Typical setup:
- Create a Render **Web Service** pointing to the `server/` folder
- Environment: `Node`
- Build command: `npm install && npm run build`
- Start command: `npm start`
- Add env vars in Render dashboard:
  - `MONGODB_URI`
  - `JWT_SECRET`
  - `JWT_REFRESH_SECRET` (production)
  - `TOMTOM_API_KEY`
  - `REDIS_URL` (optional)

### Android: production base URL
- Set `MONGO_API_BASE_URL` to your Render service URL (HTTPS), e.g. `https://your-service.onrender.com/`

---

## Tech stack

### Android
- Kotlin, Jetpack Compose
- WorkManager (background sync)
- Room (offline/history storage)
- Koin (DI)

### Backend
- Node.js, TypeScript, Express
- Mongoose (MongoDB)
- ioredis + rate-limiter-flexible (optional)

---

## Troubleshooting

### Emulator can’t reach backend
If your backend runs on your host machine:
- Android emulator should use `http://10.0.2.2:<port>/`

### 401/403 auth problems
- Ensure you’re sending `Authorization: Bearer <accessToken>` to protected endpoints
- Ensure server tokens are configured (`JWT_SECRET`, `JWT_REFRESH_SECRET`)

### TomTom calls failing
- Confirm `TOMTOM_API_KEY` is set in `server/.env` or Render environment variables

---

## Contributing / Development notes

- Don’t commit secrets: `.env`, `local.properties`, keys.
- Keep API keys on the backend when possible.
- Prefer adding test fixtures under `app/src/test/resources` and `server/src/__tests__`.

---

## License

No license file is included yet. If you want, I can add an MIT license (or your preferred one).
