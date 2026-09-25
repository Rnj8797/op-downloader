# OP Downloader - Database Architecture & ER Diagram

## 1. Relational Database Design (PostgreSQL 16)

The persistent storage is modeled with security and privacy as first-class constraints. Raw source URLs containing sensitive parameters or tokens are never stored directly in plain text; instead, deterministic cryptographic hashes (`source_url_hash = SHA-256(canonical_url)`) and sanitized provider references are persisted.

### 1.1 Entity-Relationship Diagram

```mermaid
erDiagram
    USERS ||--o{ DEVICES : owns
    USERS ||--o{ SESSIONS : has
    USERS ||--o{ DOWNLOAD_JOBS : initiates
    DEVICES ||--o{ DOWNLOAD_JOBS : triggers
    DOWNLOAD_JOBS ||--|{ DOWNLOAD_ITEMS : contains
    DOWNLOAD_JOBS ||--o| PROVIDER_METADATA : references
    USERS ||--o{ AUDIT_LOGS : records
    DEVICES ||--o{ RATE_LIMIT_RECORDS : tracks

    USERS {
        uuid id PK
        varchar email UK "nullable (anonymous default)"
        varchar password_hash "nullable (Argon2id)"
        varchar role "USER | ADMIN"
        boolean is_active
        timestamp created_at
        timestamp updated_at
    }

    DEVICES {
        uuid id PK
        uuid user_id FK
        varchar device_fingerprint_hash UK "SHA-256(HardwareId + Salt)"
        varchar client_version "e.g. 1.0.0"
        varchar os_version "e.g. Android 14"
        timestamp last_seen_at
        timestamp created_at
    }

    SESSIONS {
        uuid id PK
        uuid user_id FK
        uuid device_id FK
        varchar refresh_token_hash UK "SHA-256"
        timestamp expires_at
        boolean is_revoked
        varchar client_ip_hash
        timestamp created_at
    }

    DOWNLOAD_JOBS {
        uuid id PK
        uuid user_id FK "nullable for anonymous"
        uuid device_id FK
        varchar source_url_hash "SHA-256 of normalized URL"
        varchar provider "DIRECT | OPEN_ARCHIVE | AUTHORIZED_API"
        varchar media_type "VIDEO | IMAGE | AUDIO"
        varchar status "QUEUED | DOWNLOADING | PAUSED | COMPLETED | FAILED | CANCELLED"
        bigint file_size_bytes
        varchar quality_selected "ORIGINAL | 1080P | 720P | 480P"
        varchar error_code "UNSUPPORTED_URL | NETWORK_TIMEOUT | QUOTA_EXCEEDED"
        timestamp created_at
        timestamp started_at
        timestamp completed_at
    }

    DOWNLOAD_ITEMS {
        uuid id PK
        uuid job_id FK
        varchar safe_filename "Sanitized filename"
        varchar mime_type "video/mp4, image/jpeg"
        varchar checksum_sha256 "SHA-256 content verification"
        bigint bytes_downloaded
        bigint total_bytes
        varchar storage_path "Relative temporary scratch path"
        timestamp expires_at "TTL auto-cleanup timestamp"
    }

    PROVIDER_METADATA {
        uuid id PK
        uuid job_id FK UK
        varchar provider_id
        varchar content_author_display "Public author handle if available"
        varchar content_title_sanitized "Clean title without control chars"
        integer duration_seconds
        jsonb available_formats "JSON of safe format descriptors"
        timestamp fetched_at
    }

    RATE_LIMIT_RECORDS {
        uuid id PK
        varchar bucket_key UK "IP_HASH:yyyyMMddHH or DEVICE:uuid"
        integer request_count
        timestamp window_expires_at
    }

    AUDIT_LOGS {
        uuid id PK
        uuid user_id FK "nullable"
        varchar event_type "LOGIN | DOWNLOAD_REQUEST | RATE_LIMITED | SECURITY_FLAG"
        varchar ip_address_masked "e.g. 192.168.xxx.xxx"
        varchar user_agent_summary
        jsonb details "Non-sensitive event payload"
        timestamp created_at
    }
```

---

## 2. Table Schemas & DDL Definitions

```sql
-- Extension for UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Table: users
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE,
    password_hash VARCHAR(255),
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Table: devices
CREATE TABLE devices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    device_fingerprint_hash VARCHAR(64) NOT NULL UNIQUE,
    client_version VARCHAR(32) NOT NULL,
    os_version VARCHAR(32) NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Table: download_jobs
CREATE TABLE download_jobs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    source_url_hash VARCHAR(64) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    media_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    file_size_bytes BIGINT,
    quality_selected VARCHAR(32) NOT NULL DEFAULT 'ORIGINAL',
    error_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_download_jobs_device ON download_jobs(device_id, created_at DESC);
CREATE INDEX idx_download_jobs_status ON download_jobs(status);
CREATE INDEX idx_download_jobs_url_hash ON download_jobs(source_url_hash);

-- Table: download_items
CREATE TABLE download_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id UUID NOT NULL REFERENCES download_jobs(id) ON DELETE CASCADE,
    safe_filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(128) NOT NULL,
    checksum_sha256 VARCHAR(64),
    bytes_downloaded BIGINT NOT NULL DEFAULT 0,
    total_bytes BIGINT NOT NULL DEFAULT 0,
    storage_path VARCHAR(512),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_download_items_job ON download_items(job_id);
CREATE INDEX idx_download_items_expiry ON download_items(expires_at);

-- Table: sessions
CREATE TABLE sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id UUID REFERENCES devices(id) ON DELETE SET NULL,
    refresh_token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    client_ip_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sessions_user_device ON sessions(user_id, device_id);

-- Table: audit_logs
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    event_type VARCHAR(64) NOT NULL,
    ip_address_masked VARCHAR(64) NOT NULL,
    user_agent_summary VARCHAR(255),
    details JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_event_time ON audit_logs(event_type, created_at DESC);
```

---

## 3. Redis In-Memory State & Cache Design

Redis 7 is strictly employed as an **ephemeral fast-path datastore**. It does not hold permanent application source-of-truth records.

### 3.1 Key Space Topology
| Key Pattern | Data Structure | TTL | Purpose |
| :--- | :--- | :--- | :--- |
| `rl:ip:{ip_hash}:{window}` | String (Atomic Counter) | 3600s | Enforces IP request rate limits |
| `rl:device:{device_id}:{window}` | String (Atomic Counter) | 3600s | Enforces per-device download quota |
| `job:progress:{job_id}` | Hash (`bytes`, `speed`, `pct`) | 1800s | Real-time download progress for polling / SSE |
| `queue:downloads:active` | Sorted Set (Priority Queue) | Dynamic | Concurrency control & job scheduling |
| `lock:download:{url_hash}` | String (Redlock token) | 120s | Prevents duplicate simultaneous worker tasks |
| `token:blacklist:{jti}` | String (Revocation flag) | Token TTL | Immediate JWT access token revocation |

---

## 4. Disaster Recovery & Backup Plan

* **Backup Frequency:** Automated daily PostgreSQL physical snapshot via `pg_dump` with gzip compression and AES-256 encryption.
* **Point-in-Time Recovery (PITR):** Write-Ahead Logging (WAL) archived continuously to segregated cloud backup storage with a 7-day recovery objective.
* **Retention Policy:**
  - Audit logs: 90 days with automatic partition pruning.
  - Download job records: 30 days retention for completed/failed statuses.
  - Temporary files on worker nodes: **60-minute strict TTL** via a scheduled cron job (`find /var/media_scratch -mmin +60 -delete`).
* **Redis Recovery Strategy:** Since Redis keys are ephemeral, if a Redis node fails, cache re-hydrates gracefully from PostgreSQL without data loss.
