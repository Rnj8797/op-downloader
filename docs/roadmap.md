# OP Downloader - 10-Phase Development Roadmap

This roadmap outlines the engineering progression for building, hardening, testing, and deploying **OP Downloader** across both Android and backend systems.

---

## Roadmap Overview

```mermaid
gantt
    title OP Downloader 10-Phase Engineering Schedule
    dateFormat  X
    axisFormat %d

    section Foundations
    Phase 1: Architecture, Threat Model & Specs       :active, p1, 0, 1
    Phase 2: UI Design System & Compose Screens       :p2, 1, 2
    Phase 3: Android Core (Clean Arch, Room, MediaStore):p3, 2, 3

    section Backend & Data
    Phase 4: Backend API (FastAPI, SSRF, Auth)        :p4, 3, 4
    Phase 5: Database (PostgreSQL) & Redis State      :p5, 4, 5
    Phase 6: Authorized Download Providers Adapters   :p6, 5, 6

    section Engine & Security
    Phase 7: Background & Resumable Downloads Engine  :p7, 6, 7
    Phase 8: Security Hardening & Sandbox Isolation   :p8, 7, 8

    section Verification & Release
    Phase 9: End-to-End Testing (Unit, UI, Security)  :p9, 8, 9
    Phase 10: CI/CD & Production Deployment           :p10, 9, 10
```

---

## Phase Breakdown

### Phase 1: Architecture, ERD & Threat Modeling (Current Phase)
* **Deliverables:**
  - System and UI Architecture specifications with Mermaid diagrams (`docs/architecture.md`).
  - PostgreSQL ER diagram, DDL definitions, and Redis key topology (`docs/database.md`).
  - Standard REST API Specification with Pydantic JSON schemas and error codes (`docs/api.md`).
  - STRIDE Threat Model covering 12 threat vectors with mitigations and responses (`docs/threat-model.md`).
  - Security checklist and SSRF defense specifications (`docs/security.md`, `SECURITY.md`).
  - Repository structure and environment variable definitions (`README.md`, `.env.example`).
* **Exit Criteria:** All architectural and security documents established and validated.

### Phase 2: UI Design System & Jetpack Compose Screens
* **Deliverables:**
  - Color palette (`#0B0D10` base, `#15181D` card, `#6366F1` indigo accent) and Material 3 theme.
  - Typography scale with Inter/Roboto system fonts.
  - Core Navigation: Bottom Navigation Bar (Home, Downloads, Settings).
  - Home Screen: Brand header, Hero text, Smart Input Card, Download CTA, Supported Source disclaimer, Recent Downloads carousel.
  - Preview Dialog / Sheet: Thumbnail, video duration, file size, quality selector chips (Original, 1080p, 720p, 480p).
  - Downloads Screen: Filter tabs (All, Videos, Photos), live progress items with pause/cancel actions, completed items with Gallery badges.
  - Settings Screen: Download preferences, Appearance (Dark/Light), Security (App lock), About.
  - Splash Screen and empty state components.
* **Exit Criteria:** Interactive, responsive Compose UI rendered and validated.

### Phase 3: Android Core Architecture & Local Storage
* **Deliverables:**
  - Clean Architecture layers: Domain (UseCases, Models), Data (Repositories, DataSources), Presentation (ViewModels).
  - Room Database: Entities (`DownloadRecord`, `SettingsEntity`), DAOs, migrations.
  - Android Keystore integration with `EncryptedSharedPreferences` for secure session storage.
  - Android MediaStore helper: Scoped storage integration for `Movies/OP Downloader` and `Pictures/OP Downloader` without broad storage permissions.
  - Dependency Injection setup with Hilt.
* **Exit Criteria:** Local database and MediaStore storage functional with unit tests.

### Phase 4: Backend API (FastAPI) & Security Engine
* **Deliverables:**
  - FastAPI application structure (`app/api/v1/`, `app/core/`, `app/schemas/`).
  - SSRF Protection Engine: DNS pre-resolution, CIDR blacklisting, redirect validation.
  - Input validation: Pydantic schemas with length and regex constraints.
  - Endpoints: `/health`, `/version`, `/links/inspect`, `/downloads`, `/auth/register`, `/auth/login`, `/auth/refresh`.
  - Global error handling returning standard error envelope without leaking stack traces.
* **Exit Criteria:** FastAPI server boots, inspects authorized URLs, and rejects SSRF attempts with 400.

### Phase 5: Database (PostgreSQL) & Redis State Layer
* **Deliverables:**
  - SQLAlchemy 2.0 Async ORM models (`User`, `Device`, `DownloadJob`, `DownloadItem`, `AuditLog`).
  - Alembic database migration scripts.
  - Redis connection pool for rate limiting (token bucket) and live progress caching.
  - Audit logging middleware recording security events and request metrics.
* **Exit Criteria:** Database tables provisioned, migrations pass, Redis rate limiter throttles excessive requests.

### Phase 6: Authorized Download Provider Framework
* **Deliverables:**
  - `BaseMediaProvider` abstract interface (`can_handle`, `inspect_url`, `resolve_stream`, `validate_authorization`).
  - `DirectMediaProvider`: Handles public direct media files (MP4, WebM, JPEG, PNG) with Content-Type and Range verification.
  - `OpenArchiveProvider`: Handles open-access public domain archives (e.g. Wikimedia Commons, Archive.org public items).
  - Provider Router: Resolves links to appropriate adapter or rejects with `UNSUPPORTED_URL`.
  - Zero DRM / login bypass policy enforcement.
* **Exit Criteria:** Provider adapters correctly inspect and extract metadata from authorized sources while strictly rejecting unauthorized or DRM URLs.

### Phase 7: Background & Resumable Downloads Engine
* **Deliverables:**
  - Android **WorkManager** worker (`ResumableDownloadWorker`) with foreground notification support.
  - HTTP Range header chunk streaming (`bytes=X-Y`).
  - Ephemeral scratch file buffering (`.tmp` files) with atomic rename upon completion.
  - Network state monitoring: Auto-retry with exponential backoff on connection drop.
  - Notification management: Progress bar, download speed (MB/s), ETA, Pause/Cancel actions, completion checkmark.
* **Exit Criteria:** Interrupted downloads resume from previous offset; completed files appear instantly in device Gallery.

### Phase 8: Security Hardening & Container Sandbox Isolation
* **Deliverables:**
  - Worker isolation in Docker container: `user: nobody`, `read_only: true`, `cap_drop: [ALL]`.
  - Ephemeral scratch volume with `noexec, nosuid` and 60-minute automated TTL cleanup.
  - Nginx reverse proxy with TLS 1.3, HSTS, rate-limiting zones, and security headers.
  - R8 / ProGuard rules for Android release builds stripping debug assertions.
  - Biometric App Lock via AndroidX `BiometricPrompt`.
* **Exit Criteria:** Penetration test passes; worker container prevents arbitrary code execution and filesystem escapes.

### Phase 9: Comprehensive Testing Suite
* **Deliverables:**
  - Android Unit Tests: ViewModels, URL validation, Room DAOs, MediaStore writer.
  - Android UI Tests (Compose): Home screen paste interaction, Preview dialog, Downloads tab.
  - Backend Unit & Integration Tests: Pytest test suite for SSRF engine, API endpoints, rate limiter, auth flows.
  - Security Tests: SSRF payload injection suite, path traversal tests, oversized payload tests, token forgery tests.
* **Exit Criteria:** 100% of test suite passing with high code coverage.

### Phase 10: CI/CD Pipelines & Cloud Deployment
* **Deliverables:**
  - GitHub Actions workflows: `android.yml` (build, lint, unit tests, APK artifact), `backend.yml` (pytest, flake8, mypy), `security.yml` (CodeQL, Trivy container scan, Trufflehog secret scan).
  - Production `docker-compose.yml` and environment templates.
  - Disaster recovery documentation (`docs/troubleshooting.md`, `docs/deployment.md`).
* **Exit Criteria:** Automated GitHub Actions pipeline passes on push/PR; Docker images build and orchestrate cleanly.
