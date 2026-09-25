# OP Downloader - Security Checklist & Hardening Guide

## 1. Pre-Flight Security Checklist

### Android Application Security
- [ ] **Android Keystore:** All tokens, refresh keys, and sensitive user preferences stored exclusively in `EncryptedSharedPreferences` backed by MasterKey in Android Keystore.
- [ ] **Scoped Storage / MediaStore:** No `READ_EXTERNAL_STORAGE` or `WRITE_EXTERNAL_STORAGE` requested on Android 10+ (API 29+). Write operations strictly use `MediaStore.Images` and `MediaStore.Video` collections via `ContentResolver`.
- [ ] **No Cleartext Traffic:** `android:usesCleartextTraffic="false"` explicitly configured in `AndroidManifest.xml` and enforced by Network Security Config.
- [ ] **R8 / ProGuard Minification:** Obfuscation, class shrinking, and code optimization enabled for release builds. Debug logs and assertions stripped.
- [ ] **Clipboard Access:** Clipboard read strictly initiated on user-triggered tap action (Smart Paste button). No automated background clipboard monitoring or polling services.
- [ ] **App Lock & Biometrics:** Optional AndroidX `BiometricPrompt` with CryptoObject verification. Raw PIN codes are never stored in plain text.
- [ ] **No Insecure WebViews:** WebViews disabled or isolated with JavaScript and file access turned off by default.

### Backend & API Security
- [ ] **Strict SSRF Defense:** DNS resolved before HTTP connection; all resolved IP addresses validated against RFC 1918, RFC 3927 (AWS/GCP metadata `169.254.169.254`), loopback (`127.0.0.0/8`, `::1`), and link-local ranges.
- [ ] **Redirect Pinching:** Automatic HTTP redirect following disabled in OkHttp/httpx clients; each redirected hop is resolved and re-validated against SSRF filters.
- [ ] **No Circumvention Logic:** Zero code paths for DRM bypass, Widevine cracking, private account scraping, or CAPTCHA circumvention.
- [ ] **Input Sanitization:** URL syntax verified with Pydantic and Python `urllib.parse`. Filenames completely sanitized (regex whitelist `^[a-zA-Z0-9_\-\.]+$`, path traversal separators `/` and `\` stripped).
- [ ] **Rate Limiting:** Multi-tiered rate limiting (IP-based, device-based, user-based, concurrent download limits) backed by Redis token bucket.
- [ ] **Safe Authentication:** Passwords hashed with Argon2id; access tokens short-lived (15 minutes); rotating refresh tokens stored with SHA-256 hashes.
- [ ] **Error Sanitization:** Production errors return generic user messages and structured codes (`UNSUPPORTED_URL`, `RATE_LIMIT_EXCEEDED`). Zero stack traces or database errors exposed.
- [ ] **Security Headers:** Strict HTTPS (HSTS), `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Content-Security-Policy`, `Referrer-Policy: strict-origin-when-cross-origin`.

### Infrastructure & Container Sandbox
- [ ] **Non-Root Containers:** Docker containers run as unprivileged `appuser` (UID 10001).
- [ ] **Linux Capabilities Dropped:** `cap_drop: [ALL]` applied to worker containers.
- [ ] **Ephemeral Scratch Storage:** Temporary scratch directory mounted with `noexec, nosuid, nodev` and a 60-minute automated TTL cleanup.
- [ ] **Resource Limits:** Docker `mem_limit: 512m`, `cpus: 1.0` applied to worker containers.
- [ ] **Secret Isolation:** No secrets or private keys in Git repository; all credentials loaded via environment variables (`.env`).

---

## 2. SSRF Protection Engine Algorithm

```python
"""
Conceptual SSRF Validation Filter:
1. Parse URL & ensure scheme is https.
2. Resolve hostname to all A and AAAA records.
3. Verify every resolved IP is globally routable (not private, loopback, or cloud metadata).
4. Connect using pre-verified IP with pinned Host header to avoid DNS rebinding.
"""
import ipaddress
import socket
from urllib.parse import urlparse

BLOCKED_NETWORKS = [
    ipaddress.ip_network("0.0.0.0/8"),
    ipaddress.ip_network("10.0.0.0/8"),
    ipaddress.ip_network("127.0.0.0/8"),
    ipaddress.ip_network("169.254.0.0/16"),   # AWS/GCP Metadata
    ipaddress.ip_network("172.16.0.0/12"),
    ipaddress.ip_network("192.168.0.0/16"),
    ipaddress.ip_network("::1/128"),
    ipaddress.ip_network("fc00::/7"),
    ipaddress.ip_network("fe80::/10"),
]

def is_ip_allowed(ip_str: str) -> bool:
    ip = ipaddress.ip_address(ip_str)
    for network in BLOCKED_NETWORKS:
        if ip in network:
            return False
    return not (ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_multicast)

def validate_url_safe(url_str: str) -> bool:
    parsed = urlparse(url_str)
    if parsed.scheme.lower() != "https":
        return False
    if not parsed.hostname:
        return False
    try:
        resolved_ips = socket.getaddrinfo(parsed.hostname, parsed.port or 443)
        for entry in resolved_ips:
            ip_str = entry[4][0]
            if not is_ip_allowed(ip_str):
                return False
        return True
    except socket.gaierror:
        return False
```
