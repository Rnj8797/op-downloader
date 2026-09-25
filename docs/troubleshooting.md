# OP Downloader - Troubleshooting & Operational Runbooks

## 1. Quick Diagnostic Triage Table

| Symptom | Probable Cause | Diagnostic Command | Remediation Action |
| :--- | :--- | :--- | :--- |
| **HTTP 400 `UNSUPPORTED_URL`** | URL points to private host, LAN address, or unsupported platform | Inspect query against `SSRFProtectionError` | Verify target URL is public HTTPS and supported by an authorized provider adapter. |
| **HTTP 429 `RATE_LIMIT_EXCEEDED`** | Hourly IP or Device quota consumed | `redis-cli get "rl:ip:<hash>"` | Wait for 60-min window expiry, or flush test key with `redis-cli del`. |
| **Worker OOM / Crash Loop** | Media stream exceeded container memory limits | `docker logs op-downloader-worker` | Verify worker stream chunk size is 64KB and not buffering whole file into RAM. |
| **MediaStore Insert Failure** | App lack of Scoped Storage directory access on Android | Logcat tag `MediaStoreHelper` | Ensure Android MediaStore uses `VOLUME_EXTERNAL_PRIMARY` and clears `IS_PENDING`. |
| **Download Resumption Fails (416)** | Remote source does not support `Range` headers or file changed | Inspect server headers with `curl -I -H "Range: bytes=100-"` | ResumableWorker automatically purges stale `.tmp` cache and restarts clean stream. |

---

## 2. Detailed Root Cause Analysis (RCA) Runbooks

### RCA 1: Investigating SSRF Blocks
If a legitimate authorized media link is unexpectedly blocked:
1. Check backend logs for the resolved IP:
   ```bash
   docker logs op-downloader-api | grep "SSRF"
   ```
2. Verify if the domain resolves to an IP inside RFC 1918 or RFC 3927 metadata ranges:
   ```bash
   dig +short target-domain.com
   ```
3. If the host legitimately uses an unusual CDN that overlaps with private subnets, review `backend/app/core/ssrf.py` network whitelist policies.

### RCA 2: Flushing Ephemeral Redis State
To clear rate-limiting locks during QA or automated staging:
```bash
# Connect to Redis container
docker exec -it op-downloader-redis redis-cli -a "$REDIS_PASSWORD"

# Inspect active keys
KEYS "rl:*"

# Delete rate-limit bucket for a specific IP hash
DEL "rl:ip:b6d81b360a569acf5e9ec10c45070469"
```

### RCA 3: Worker Scratch Disk Exhaustion
If the ephemeral scratch volume `/var/media_scratch` reaches capacity:
1. Check disk utilization:
   ```bash
   docker exec op-downloader-worker df -h /var/media_scratch
   ```
2. Manually trigger the TTL garbage collection:
   ```bash
   docker exec op-downloader-worker python -c '
   from app.workers.tasks import ResumableStreamDownloader
   ResumableStreamDownloader().purge_expired_scratch_files(ttl_minutes=15)
   '
   ```
