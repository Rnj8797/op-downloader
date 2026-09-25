# OP Downloader - REST API Specification (v1)

## 1. Global Standards & Conventions

* **Base URL:** `https://api.opdownloader.app/api/v1`
* **Transport:** HTTPS with TLS 1.3 only. HTTP is permanently disabled.
* **Content-Type:** `application/json; charset=utf-8`
* **Authentication:** Bearer Token via `Authorization: Bearer <access_token>` header. Anonymous access uses a mandatory `X-Device-Id` header with strict quota constraints.
* **Idempotency:** Non-safe mutations accept optional `Idempotency-Key` UUID headers.

### Standard Response Envelope
All API responses adhere strictly to the following unified JSON schemas:

#### Success Envelope (`200 OK`, `201 Created`, `202 Accepted`)
```json
{
  "success": true,
  "data": { ... }
}
```

#### Error Envelope (`400`, `401`, `403`, `404`, `429`, `500`)
```json
{
  "success": false,
  "error": {
    "code": "UNSUPPORTED_URL",
    "message": "This link isn't supported."
  }
}
```

> **Security Rule:** Internal stack traces, raw database error strings, backend IPs, internal server names, or external provider keys are **never** returned in the error payload.

---

## 2. API Endpoints

### 2.1 System & Health

#### `GET /health`
Returns system component readiness (FastAPI, PostgreSQL, Redis, Worker pool).
* **Auth:** Public
* **Response `200 OK`:**
```json
{
  "success": true,
  "data": {
    "status": "healthy",
    "database": "connected",
    "redis": "connected",
    "workers_available": 4
  }
}
```

#### `GET /version`
Returns current API version and minimal supported client builds.
* **Auth:** Public
* **Response `200 OK`:**
```json
{
  "success": true,
  "data": {
    "version": "1.0.0",
    "minimum_android_version": "1.0.0",
    "maintenance_mode": false
  }
}
```

---

### 2.2 Link Inspection & Validation

#### `POST /links/inspect`
Inspects an authorized public URL, validates syntax and SSRF compliance, and queries the appropriate provider adapter for available formats and media metadata.

* **Headers:** `X-Device-Id: <UUID>`
* **Request Body:**
```json
{
  "url": "https://images.unsplash.com/photo-1579783902614-a3fb3927b675"
}
```

* **Response `200 OK`:**
```json
{
  "success": true,
  "data": {
    "provider": "DIRECT_MEDIA",
    "media_type": "IMAGE",
    "title": "photo-1579783902614-a3fb3927b675",
    "thumbnail_url": "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=200",
    "duration_seconds": null,
    "available_formats": [
      {
        "format_id": "original",
        "quality_label": "Original",
        "estimated_size_bytes": 4410291,
        "mime_type": "image/jpeg"
      },
      {
        "format_id": "compressed_1080p",
        "quality_label": "1080p",
        "estimated_size_bytes": 1245192,
        "mime_type": "image/jpeg"
      }
    ]
  }
}
```

* **Error `400 Bad Request`:**
```json
{
  "success": false,
  "error": {
    "code": "UNSUPPORTED_URL",
    "message": "This link isn't supported."
  }
}
```

---

### 2.3 Download Jobs Management

#### `POST /downloads`
Submits a download job for processing by the background worker pool.

* **Headers:** `X-Device-Id: <UUID>`, `Authorization: Bearer <Token>` (Optional)
* **Request Body:**
```json
{
  "url": "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
  "format_id": "original",
  "safe_title": "BigBuckBunny"
}
```

* **Response `202 Accepted`:**
```json
{
  "success": true,
  "data": {
    "job_id": "d3b07384-d113-494a-81a1-9457223b2468",
    "status": "QUEUED",
    "provider": "DIRECT_MEDIA",
    "media_type": "VIDEO",
    "estimated_size_bytes": 158008374,
    "created_at": "2026-09-25T16:05:00Z"
  }
}
```

#### `GET /downloads`
Lists current and past download jobs for the authenticated user or device.

* **Query Parameters:** `status` (optional: `ACTIVE`, `COMPLETED`), `limit` (default 20, max 50), `offset`
* **Response `200 OK`:**
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "job_id": "d3b07384-d113-494a-81a1-9457223b2468",
        "safe_filename": "BigBuckBunny.mp4",
        "media_type": "VIDEO",
        "status": "COMPLETED",
        "bytes_downloaded": 158008374,
        "total_bytes": 158008374,
        "completed_at": "2026-09-25T16:07:15Z"
      }
    ],
    "total": 1
  }
}
```

#### `GET /downloads/{id}`
Fetches real-time status, progress percentage, download speed, and temporary retrieval URLs.

* **Response `200 OK`:**
```json
{
  "success": true,
  "data": {
    "job_id": "d3b07384-d113-494a-81a1-9457223b2468",
    "status": "DOWNLOADING",
    "progress_percentage": 68.4,
    "bytes_downloaded": 108077727,
    "total_bytes": 158008374,
    "speed_bytes_per_sec": 8808038,
    "eta_seconds": 6,
    "temporary_download_url": null
  }
}
```

#### `POST /downloads/{id}/pause`
Pauses an ongoing active download job.

#### `POST /downloads/{id}/resume`
Resumes a paused download with HTTP Range resumption.

#### `POST /downloads/{id}/cancel`
Cancels the download and purges temporary buffer chunks.

#### `DELETE /downloads/{id}`
Deletes the download record from server history and cleans up any transient files.

---

### 2.4 Authentication & Device Session Management

#### `POST /auth/register`
* **Request Body:** `{ "email": "user@example.com", "password": "SecurePassword123!" }`
* **Response `201 Created`:** User account created. Returns JWT access token (15-min TTL) and refresh token (30-day TTL).

#### `POST /auth/login`
* **Request Body:** `{ "email": "user@example.com", "password": "SecurePassword123!" }`
* **Response `200 OK`:**
```json
{
  "success": true,
  "data": {
    "access_token": "eyJhbGciOi...",
    "refresh_token": "def5020...",
    "token_type": "bearer",
    "expires_in": 900
  }
}
```

#### `POST /auth/refresh`
* **Request Body:** `{ "refresh_token": "def5020..." }`
* **Response `200 OK`:** Issues new access token and rotated refresh token. Revokes previous refresh token.

#### `POST /auth/logout`
Revokes active session tokens in Redis blacklist and database.

---

## 3. Error Codes Reference Table
| Error Code | HTTP Status | Standard User Message |
| :--- | :--- | :--- |
| `UNSUPPORTED_URL` | 400 | This link isn't supported. |
| `SSRF_BLOCKED` | 400 | Invalid or restricted network address. |
| `RATE_LIMIT_EXCEEDED` | 429 | Rate limit reached. Please wait before trying again. |
| `UNAUTHORIZED` | 401 | Authentication is required to access this resource. |
| `TOKEN_EXPIRED` | 401 | Your session has expired. Please sign in again. |
| `RESOURCE_NOT_FOUND` | 404 | The requested item could not be found. |
| `MEDIA_UNAVAILABLE` | 410 | This media is no longer available from the source. |
| `MEDIA_TOO_LARGE` | 413 | Media size exceeds maximum permitted file size. |
| `SERVER_ERROR` | 500 | Something went wrong. Please try again later. |
