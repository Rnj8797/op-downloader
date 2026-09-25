# OP Downloader 📥

> **Modern, Fast, Secure Media Downloader for Android**  
> *Save authorized public and user-owned media directly to your device Gallery with high-speed, resumable streaming and zero circumvention.*

[![Security Policy](https://img.shields.io/badge/Security-Strict%20Defense--in--Depth-blue.svg)](SECURITY.md)
[![Android](https://img.shields.io/badge/Android-Jetpack%20Compose-green.svg)](android/)
[![Backend](https://img.shields.io/badge/Backend-FastAPI-teal.svg)](backend/)
[![Database](https://img.shields.io/badge/Database-PostgreSQL%20%2B%20Redis-red.svg)](docs/database.md)

---

## 1. Project Overview & Philosophy

**OP Downloader** is an enterprise-grade Android application paired with a hardened backend service. It enables users to paste an authorized link and reliably download media directly into their device's MediaStore (`Movies/OP Downloader` or `Pictures/OP Downloader`).

### ⚖️ Legal & Security Standards
* **No DRM Bypass:** Strictly rejects Widevine, FairPlay, or encrypted streams.
* **No Authentication / Access-Control Circumvention:** Zero support for bypassing login gates, paywalls, private profiles, or CAPTCHA.
* **Authorized Sources Only:** Strictly limited to direct media files (MP4, WebM, JPEG, PNG) and platform APIs with explicit public download permission.
* **SSRF Guard:** Backend enforces strict DNS pre-resolution and blocks all RFC 1918, RFC 3927 (cloud metadata `169.254.169.254`), loopback, and private network addresses.

---

## 2. Branding & Design System

* **Brand Personality:** Modern, Fast, Minimal, Premium, Trustworthy.
* **Color Palette (Dark-First):**
  - **Base Background:** `#0B0D10`
  - **Card Background:** `#15181D`
  - **Primary Text:** `#FFFFFF`
  - **Secondary Text:** `#9CA3AF`
  - **Brand Accent:** `#6366F1` (Indigo) with `#8B5CF6` (Violet) gradient highlights
  - **Status:** Success `#22C55E` | Warning `#F59E0B` | Error `#EF4444`

---

## 3. Directory Layout

```text
op-downloader/
├── .github/
│   └── workflows/
│       ├── android.yml          # Android build, lint, and unit tests
│       ├── backend.yml          # Python linting, pytest, type checks
│       └── security.yml         # CodeQL, TruffleHog, Trivy scanning
├── android/                     # Android Application (Kotlin, Jetpack Compose, Hilt)
│   ├── app/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── java/com/opdownloader/app/
│   │   │   │   │   ├── core/         # Theme, Design System, Utils
│   │   │   │   │   ├── data/         # Room DB, MediaStore, Network
│   │   │   │   │   ├── domain/       # UseCases, Models, Validation
│   │   │   │   │   ├── presentation/ # Compose Screens (Home, Downloads, Settings)
│   │   │   │   │   └── worker/       # WorkManager Resumable Download Worker
│   │   │   │   └── AndroidManifest.xml
│   │   │   └── test/
│   │   └── build.gradle.kts
│   └── build.gradle.kts
├── backend/                     # FastAPI Backend & Worker Service
│   ├── app/
│   │   ├── api/v1/              # REST Endpoints (links, downloads, auth)
│   │   ├── core/                # Config, Security, SSRF Validator
│   │   ├── db/                  # SQLAlchemy Models, Session, Migrations
│   │   ├── providers/           # Pluggable Provider Adapters
│   │   ├── schemas/             # Pydantic Request/Response Models
│   │   └── workers/             # Download Queue Worker Tasks
│   ├── Dockerfile
│   ├── Dockerfile.worker
│   ├── pyproject.toml
│   └── requirements.txt
├── docs/                        # Specifications & Architecture
│   ├── architecture.md          # System & UI Architecture
│   ├── database.md              # ER Diagram, Schemas & Redis Topology
│   ├── api.md                   # REST API Specification (v1)
│   ├── threat-model.md          # STRIDE Threat Model (12 Vectors)
│   ├── security.md              # Security Checklist & Hardening Guide
│   ├── roadmap.md               # 10-Phase Development Roadmap
│   ├── deployment.md            # Cloud Deployment & Docker Guide
│   └── troubleshooting.md       # Diagnostic Runbooks
├── infrastructure/              # Nginx reverse proxy configs & TLS setup
│   └── nginx/
├── tests/                       # End-to-end and security penetration tests
├── docker-compose.yml           # Isolated multi-container local & prod setup
├── .env.example                 # Environment configuration template
├── SECURITY.md                  # Vulnerability reporting & security boundaries
├── LICENSE                      # Apache 2.0 / MIT License
└── README.md
```

---

## 4. Development Roadmap (10 Phases)

1. **Phase 1: Architecture, ERD & Threat Model** *(Current)*
2. **Phase 2: UI Design System & Compose Screens**
3. **Phase 3: Android Core (Clean Arch, Room, MediaStore)**
4. **Phase 4: Backend API (FastAPI, SSRF Defense, Auth)**
5. **Phase 5: Database (PostgreSQL) & Redis State Layer**
6. **Phase 6: Authorized Download Provider Framework**
7. **Phase 7: Background & Resumable Downloads Engine**
8. **Phase 8: Security Hardening & Container Sandbox Isolation**
9. **Phase 9: Comprehensive Testing Suite**
10. **Phase 10: CI/CD Pipelines & Release Validation**

---

## 5. Getting Started & Installation

### Android Application
* **Supported Versions:** Android 8.0 (API 26) through Android 14, 15, and Android 16 (API 36).
* **Build Requirements:** OpenJDK 17+, Gradle 8.4+, Android SDK 34+.
* **Build Command:**
  ```bash
  cd android
  ./gradlew assembleRelease
  ```
* **Installation:**
  ```bash
  adb install -r android/app/build/outputs/apk/release/app-release.apk
  ```

### Backend Services
```bash
# 1. Copy environment template
cp .env.example .env

# 2. Start PostgreSQL 16, Redis 7, FastAPI Gateway, and Worker Sandbox
docker compose up -d --build

# 3. Verify health status
curl http://localhost:8000/api/v1/health
```

---

## 6. Security Model & Architecture

* **SSRF Guard:** Strict DNS pre-resolution, IP parsing (including decimal, hex, and IPv4-mapped IPv6), and manual redirect hopping verification (`safe_fetch_with_redirect_pinching`).
* **Memory Safety:** Streaming reads bounded to 64KB buffers (`aiter_bytes`). Media files are never buffered entirely in RAM.
* **Storage Protection:** Enforces 2GB maximum limit (`MAX_DOWNLOAD_SIZE_BYTES`) against infinite-stream DoS attacks.
* **Worker Sandbox:** Runs as non-root user `10001:10001`, `cap_drop: [ALL]`, and `read_only: true` with a dedicated temporary `tmpfs` volume.
* **Data Minimization:** User source URLs are stored only as SHA-256 hashes (`source_url_hash`), protecting user privacy.
* **Android Keystore:** Hardware-backed AES-256-GCM encryption for stored tokens and configuration data.

---

## 7. Operational Limitations & Disclaimers

> [!IMPORTANT]
> **Operational & Compliance Disclaimers:**
> - **No "100% Secure" or "Unhackable" Claims:** Security is an ongoing practice of defense-in-depth and active monitoring. Passing automated test suites validates implemented mitigations against known attack vectors under tested conditions.
> - **Strictly Authorized Sources:** OP Downloader does not circumvention DRM, CAPTCHAs, private accounts, paywalls, or access controls. Only explicitly authorized public links, direct media URLs, or user-owned content are supported.
> - **Platform Compatibility:** Compatibility is not guaranteed for arbitrary closed third-party web platforms that do not provide direct media URLs or public download APIs.
> - **Fair Use Limits:** Standard rate limiting (10 req/hr anonymous, 50 req/hr authenticated) and concurrency limits (max 2 active downloads) are strictly enforced to prevent abuse.

