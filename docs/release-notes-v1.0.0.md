# OP Downloader v1.0.0 — Official Release Notes

> **Version:** `1.0.0` (Target Release Tag: `v1.0.0`)  
> **Target Platforms:** Android 8.0 - Android 16 (API Level 26-36+) & Linux Container Backend  
> **Release Candidate Status:** RC1 passed all local and physical device validations.  

---

## 🚀 Release Highlights

**OP Downloader** is a high-speed, secure media saver for Android devices. Designed with defense-in-depth security principles and modern Material 3 design, it provides reliable, streaming downloads directly into the Android Gallery (`Movies/OP Downloader` and `Pictures/OP Downloader`) without loading entire files into device memory.

### 🛡️ Core Security Architecture & Hardening
- **SSRF Immunity:** Multi-layered URL validation parser rejecting IPv4/IPv6 loopbacks, cloud metadata (`169.254.169.254`, `100.100.100.200`), RFC 1918 private subnets, decimal IP integer formats, hex hostnames, and IPv4-mapped IPv6 addresses.
- **Redirect Pinning:** All intermediate HTTP 3xx hops are manually intercepted and verified before establishing subsequent connections (`safe_fetch_with_redirect_pinching`).
- **Memory Safety:** Streaming reads bounded strictly to 64KB buffers (`aiter_bytes`).
- **Storage Boundaries:** 2GB maximum media limit enforced against infinite-stream denial-of-service attacks.
- **Isolated Worker Sandbox:** Docker container worker runs as unprivileged user `10001:10001`, with `cap_drop: [ALL]`, read-only root filesystem, and dedicated `tmpfs` scratch volume (`noexec, nosuid`).
- **Multi-Tenant Isolation:** Backend endpoints strictly isolate user jobs. Unauthorized cross-user job access returns `404 Not Found`.
- **Data Minimization:** User source URLs are permanently stored only as deterministic SHA-256 hashes (`source_url_hash`).

### 📱 Android Application & UI Experience
- **Material 3 UI:** Premium dark-first theme with dynamic animations and accessible typography.
- **Smart Paste:** Safe clipboard inspection triggered strictly upon direct user action.
- **Format & Quality Selection:** Interactive bottom sheet providing stream resolution choices (e.g. 1080p, 720p, 480p) and file size estimates.
- **Scoped Storage:** Android 10+ MediaStore API integration with `IS_PENDING = 1` atomic completion locks.
- **Background Persistence:** WorkManager `ResumableDownloadWorker` persists downloads across application and system restarts.
- **Keystore Encryption:** Hardware-backed Android Keystore with AES-256-GCM.
- **ProGuard / R8:** Strips all debug logging from release builds. Cleartext HTTP is globally prohibited.

### ⚖️ Legal & Anti-Circumvention Compliance
OP Downloader is explicitly designed for authorized public workflows and user-owned media:
- Zero DRM circumvention (Widevine, FairPlay, PlayReady streams rejected).
- Zero CAPTCHA or paywall bypass.
- Zero private account scraping or session hijacking.

---

## 🧪 Testing & Validation Summary

| Test Phase | Scope | Result |
| :--- | :--- | :---: |
| **Backend Core & SSRF** | Parity tests, IP encoding fuzzing, redirect pinning | :white_check_mark: **PASS** |
| **Database & Cache** | PostgreSQL schemas, Redis sliding-window rate limiting | :white_check_mark: **PASS** |
| **Resumable Streaming** | 64KB bounded chunks, Range header resumption, TTL cleanup | :white_check_mark: **PASS** |
| **Security Hardening** | ProGuard rules, container caps, Nginx security headers | :white_check_mark: **PASS** |
| **Penetration Tests** | 10 SSRF vectors, path traversal, null bytes, JWT tampering | :white_check_mark: **PASS** |
| **End-to-End Suite** | 17 comprehensive integration scenarios (Phases L) | :white_check_mark: **PASS** |
| **Physical Hardware** | Tested on Vivo I2403 running **Android 16** (API 36) via ADB | :white_check_mark: **PASS** |

---

## 📦 Release Artifacts

* **Release APK:** `android/app/build/outputs/apk/release/app-release.apk`
  * **Size:** 7.16 MB (7,512,940 bytes)
  * **SHA-256 Checksum:** `c3b28c86302601a9f9dcc9a76cc49feaceacd8e9db8cf8d6d1227396ee3fe13a`
  * **Application ID:** `com.opdownloader.app`
  * **Version:** `1.0.0` (`versionCode: 1`)
  * **Build Type:** Release / R8 Minified / Non-debuggable

---

## ⚠️ Known Operational Limitations

1. **Production Keystore Signing:** The release candidate binary is packaged and validated with standard staging configurations. Production distribution via Google Play / GitHub Releases requires signing with the organization's official release signing key.
2. **Third-Party Domain Changes:** Third-party media hosts may change endpoint URL structures or public response headers over time. The pluggable provider architecture (`app/providers/`) enables updating specific provider adapters independently.
3. **No Unrestricted Platform Access:** Closed platforms without direct media URLs or official public download APIs cannot be downloaded.
