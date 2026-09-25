# OP Downloader - Comprehensive Threat Model

This document establishes the threat vectors, risk evaluation, mitigations, automated detection methods, and incident response runbooks for **OP Downloader**, adhering to OWASP API Security Top 10 and STRIDE methodologies.

---

## 1. Summary Matrix

| # | Threat Vector | STRIDE Category | Impact | Likelihood | Risk Rating |
|---|---|---|---|---|---|
| 1 | Server-Side Request Forgery (SSRF) | Information Disclosure / Tampering | Critical | High | **CRITICAL** |
| 2 | API Abuse / Resource Exhaustion | Denial of Service | High | High | **HIGH** |
| 3 | Distributed Denial of Service (DDoS) | Denial of Service | High | Medium | **HIGH** |
| 4 | Credential Theft & Brute Force | Spoofing / Elevation of Privilege | High | Medium | **HIGH** |
| 5 | Token Theft & Replay Attacks | Spoofing | High | Medium | **HIGH** |
| 6 | Malicious File & Polyglot Injection | Elevation of Privilege / Tampering | Critical | Medium | **HIGH** |
| 7 | Path Traversal & Arbitrary File Overwrite | Tampering / Information Disclosure | Critical | Low | **HIGH** |
| 8 | Dependency Supply Chain Vulnerabilities | Elevation of Privilege / Tampering | High | Medium | **HIGH** |
| 9 | Compromised Worker Node Sandbox Escape | Elevation of Privilege | Critical | Low | **HIGH** |
| 10| Database Compromise & SQL Injection | Information Disclosure / Tampering | Critical | Low | **HIGH** |
| 11| Secret & Key Leakage | Information Disclosure | Critical | Low | **HIGH** |
| 12| Abusive Download Automation & Bot Flooding | Denial of Service / Repudiation | Medium | High | **MEDIUM** |

---

## 2. In-Depth Threat Analysis & Mitigations

### Threat 1: Server-Side Request Forgery (SSRF)
* **Threat:** An attacker supplies URLs pointing to internal infrastructure (e.g., `http://169.254.169.254/latest/meta-data`, `http://10.0.0.1`, `http://localhost:5432`, or DNS rebinding hosts) to enumerate or access internal services.
* **Impact:** Exfiltration of cloud credentials, internal network mapping, unauthorized database/Redis tampering.
* **Likelihood:** High (any downloader accepts user-defined URLs).
* **Mitigation:**
  - Reject non-HTTPS schemes (`file://`, `ftp://`, `gopher://`, `http://`).
  - Pre-request DNS resolution with IP verification against a blacklist of private CIDRs:
    `127.0.0.0/8`, `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `169.254.0.0/16`, `::1/128`, `fc00::/7`, `fe80::/10`.
  - Disable automatic redirects; inspect every redirect destination IP before re-querying.
  - Pin DNS to prevent Time-of-Check to Time-of-Use (TOCTOU) DNS rebinding attacks.
  - Run downloader workers in an isolated VPC without access to cloud metadata services or internal databases.
* **Detection:** Real-time alerting on blocked private IP resolution attempts; log query alerts for `169.254.` or internal hostnames.
* **Response:** Immediately drop connection, log `SSRF_ATTEMPT` audit event, block requesting IP/device for 24 hours.

---

### Threat 2: API Abuse & Resource Exhaustion
* **Threat:** Malicious actors flood the `/api/v1/links/inspect` or `/api/v1/downloads` endpoints with oversized payloads or excessive requests.
* **Impact:** Server CPU/memory exhaustion, database connection starvation, degraded service for legitimate users.
* **Likelihood:** High.
* **Mitigation:**
  - Strict token bucket rate limiting in Redis by IP and `X-Device-Id`.
  - Maximum payload size limit enforced at Nginx (max 32KB for JSON requests).
  - Pydantic validation with bounded field lengths (e.g., URL length max 2048 chars).
  - Maximum concurrent download limit per user (default 2).
* **Detection:** Redis rate-limit trigger counters, Nginx 429 response rate spikes.
* **Response:** Return HTTP `429 Too Many Requests` with `Retry-After` header. Exponentially lengthen throttling window for persistent offenders.

---

### Threat 3: Distributed Denial of Service (DDoS)
* **Threat:** Volumetric network floods or application-layer HTTP floods targeting the API gateway.
* **Impact:** Complete service outage.
* **Likelihood:** Medium.
* **Mitigation:**
  - Cloudflare / AWS CloudFront edge protection with SYN flood protection, geo-blocking, and WAF rules.
  - Connection rate limiting at Nginx edge (`limit_conn_zone`, `limit_req_zone`).
* **Detection:** Edge traffic anomaly alarms, drop in incoming healthy connection ratios.
* **Response:** Enable Cloudflare "Under Attack" mode, deploy edge challenge rules.

---

### Threat 4: Credential Theft & Brute Force
* **Threat:** Automated credential stuffing, password guessing against user accounts.
* **Impact:** Account takeover, unauthorized job visibility.
* **Likelihood:** Medium.
* **Mitigation:**
  - Argon2id password hashing with high memory cost parameters (`time_cost=3, memory_cost=65536, parallelism=4`).
  - Progressive login delays and account lockout after 5 failed attempts within 15 minutes.
  - Generic authentication error messages ("Invalid email or password").
* **Detection:** High frequency of failed login attempts grouped by username or subnet.
* **Response:** Lock affected account, dispatch password reset notification, require email confirmation.

---

### Threat 5: Token Theft & Replay Attacks
* **Threat:** Interception of JWT access tokens or refresh tokens from compromised devices or man-in-the-middle attacks.
* **Impact:** Impersonation of legitimate user sessions.
* **Likelihood:** Medium.
* **Mitigation:**
  - HTTPS / TLS 1.3 only with HSTS (`max-age=31536000; includeSubDomains; preload`).
  - Short-lived access tokens (10–15 minutes).
  - Rotating refresh tokens (each use invalidates the prior token and creates a new one).
  - On Android: Tokens stored strictly in **Android Keystore** backed `EncryptedSharedPreferences`. Never plain text.
  - Immediate token revocation support via Redis JTI blacklist.
* **Detection:** Concurrent token usage from geographically impossible IP locations or reused revoked refresh tokens.
* **Response:** Revoke all sessions associated with user account, prompt re-authentication.

---

### Threat 6: Malicious File & Polyglot Injection
* **Threat:** Download source delivers disguised executable files, polyglot ZIP/JPEGs, or malware targeting Android or worker parser vulnerabilities.
* **Impact:** Client device infection, worker node remote code execution.
* **Likelihood:** Medium.
* **Mitigation:**
  - Magic byte inspection (validate MIME header against declared media types: `video/mp4`, `image/jpeg`, etc.).
  - Reject executable headers (e.g., ELF, MZ/PE, DEX, script tags).
  - Strictly enforce sanitized file extension mapping based on verified MIME type, not remote filename.
  - Worker filesystem mounted with `noexec, nosuid, nodev`.
* **Detection:** MIME mismatch between HTTP `Content-Type` and magic byte signature.
* **Response:** Abort download immediately, delete temporary chunk, mark job as `FAILED_SECURITY_CHECK`.

---

### Threat 7: Path Traversal & Arbitrary File Overwrite
* **Threat:** User or remote header injects malicious file names containing `../`, null bytes `%00`, or absolute paths (`/etc/passwd`, `C:\Windows\...`).
* **Impact:** Overwriting system binaries or arbitrary file reading on the server or Android client.
* **Likelihood:** Low (with strict sanitization).
* **Mitigation:**
  - Complete replacement of untrusted filenames with UUID or regex-whitelisted alphanumeric safe strings: `^[a-zA-Z0-9_\-\.]+$`.
  - Discard all path separators (`/`, `\`).
  - On Android: Write solely via **MediaStore Scoped Storage** using `ContentResolver.insert()` into managed directories (`Movies/OP Downloader`, `Pictures/OP Downloader`).
* **Detection:** Regex alert on `..`, `%2e%2e`, `/`, or `\` in filename sanitization logs.
* **Response:** Discard provided name, fallback to deterministic timestamp format `OP_YYYYMMDD_HHMMSS.ext`.

---

### Threat 8: Dependency Supply Chain Vulnerabilities
* **Threat:** Vulnerable or malicious third-party dependencies in Python (pip) or Android (Gradle).
* **Impact:** Remote code execution, dependency backdoors.
* **Likelihood:** Medium.
* **Mitigation:**
  - Pinned versions with cryptographic hashes (`poetry.lock` or `requirements.txt --require-hashes`, Gradle dependency locking).
  - Automated CI scans using GitHub Dependabot, Snyk, and CodeQL.
  - Minimal container base images (`python:3.12-slim`).
* **Detection:** Daily vulnerability scanner alerts (CVE triggers).
* **Response:** Triage within 24 hours, patch and deploy emergency hotfix.

---

### Threat 9: Compromised Worker Node Sandbox Escape
* **Threat:** Exploit targeting video decoders (e.g., libavcodec / ffmpeg vulnerability) in worker processes to break out of container.
* **Impact:** Host server compromise, lateral network movement.
* **Likelihood:** Low.
* **Mitigation:**
  - Worker containers run with an unprivileged non-root user (`USER nobody` / `uid 10001`).
  - Drop all capabilities (`--cap-drop=ALL`).
  - Read-only root filesystem (`--read-only`) with a dedicated ephemeral `tmpfs` for scratch downloads.
  - Strict cgroup resource limits (`--cpus=1.0`, `--memory=512m`).
  - No access to Docker socket or host filesystem.
* **Detection:** Container crash loop alerts, out-of-memory kernel alerts, unauthorized syscall detections via auditd/seccomp.
* **Response:** Container is automatically destroyed and replaced by orchestration daemon.

---

### Threat 10: Database Compromise & SQL Injection
* **Threat:** SQL injection through user input or improper parameter concatenation.
* **Impact:** Data breach, user credential theft, data deletion.
* **Likelihood:** Low.
* **Mitigation:**
  - 100% Parameterized queries via SQLAlchemy ORM / Asyncpg. Zero raw string interpolation.
  - Strict database principle of least privilege (app role cannot drop tables or access system catalogs).
  - Sensitive source URLs hashed with SHA-256 before storage.
* **Detection:** Database syntax error alerts, WAF SQLi signature matches.
* **Response:** Terminate DB sessions, freeze compromised database credentials, initiate PITR restore.

---

### Threat 11: Secret & Key Leakage
* **Threat:** Committing API keys, database passwords, or signing keys to Git repositories or build logs.
* **Impact:** Cloud resource compromise, unauthorized API access.
* **Likelihood:** Low.
* **Mitigation:**
  - Pre-commit hooks (`git-secrets`, `trufflehog`) blocking commits with credentials.
  - Secrets loaded strictly via environment variables or cloud secret managers (GCP Secret Manager / AWS Secrets Manager).
  - Android release keystores stored in secure CI secrets, never checked into version control.
  - `.gitignore` configured comprehensively.
* **Detection:** GitHub secret scanning alerts, automated CI secret scans.
* **Response:** Immediately rotate compromised credentials, invalidate active sessions, audit cloud access logs.

---

### Threat 12: Abusive Download Automation & Bot Flooding
* **Threat:** Automated scrapers or scripts abusing OP Downloader as a proxy downloader to consume bandwidth.
* **Impact:** Provider IP bans, cloud bandwidth cost spikes.
* **Likelihood:** High.
* **Mitigation:**
  - Device attestation (Android SafetyNet / Play Integrity API validation).
  - Behavioral rate limiting and anomaly scoring.
  - Maximum monthly and hourly quota limits per device fingerprint.
* **Detection:** High-concurrency continuous requests with zero UI interaction intervals.
* **Response:** Enforce CAPTCHA challenge or temporary device ban.
