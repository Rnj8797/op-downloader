# OP Downloader - Production Deployment Guide

## 1. Production Architecture Overview

The production environment consists of 4 isolated tiers orchestrated via Docker Compose or Kubernetes:
1. **Edge Reverse Proxy (Nginx):** Terminates TLS 1.3, manages HSTS, applies security headers, and enforces IP connection rate limiting.
2. **FastAPI Gateway (`api`):** Stateless REST gateway handling authentication, request validation, and SSRF filtering.
3. **Data Layer (`postgres` & `redis`):** PostgreSQL 16 for persistent relational records; Redis 7 for rate-limiting token buckets and active concurrency locks.
4. **Worker Sandbox (`worker`):** Non-root, capability-dropped containers running isolated streaming download tasks with a 60-minute automated scratch TTL.

---

## 2. Server Prerequisites

* Linux Host (Ubuntu 22.04 LTS or 24.04 LTS recommended)
* Docker Engine 24.0+ and Docker Compose v2+
* 2 vCPUs, 4 GB RAM minimum
* Public DNS A Record pointing `api.opdownloader.app` to your server IP
* Ports 80 and 443 open in host firewall (UFW / Security Group)

---

## 3. Step-by-Step Deployment Runbook

### Step 1: Clone Repository & Configure Environment
```bash
git clone https://github.com/opdownloader/op-downloader.git /opt/op-downloader
cd /opt/op-downloader

# Generate production environment
cp .env.example .env
chmod 600 .env

# Generate secure random secrets
JWT_SECRET=$(openssl rand -hex 32)
POSTGRES_PASS=$(openssl rand -hex 24)
REDIS_PASS=$(openssl rand -hex 24)

sed -i "s/change_this_to_a_random_64_character_hex_string_in_production/$JWT_SECRET/" .env
sed -i "s/generate_a_secure_database_password_here/$POSTGRES_PASS/g" .env
sed -i "s/generate_a_secure_redis_password_here/$REDIS_PASS/g" .env
```

### Step 2: SSL Certificate Provisioning (Let's Encrypt / Certbot)
```bash
# Provision certificate using Certbot standalone mode
sudo apt update && sudo apt install -y certbot
sudo certbot certonly --standalone -d api.opdownloader.app

# Mount certificate directory into /etc/nginx/ssl in docker-compose.yml
```

### Step 3: Launch Containers
```bash
# Build and start all services in detached mode
docker compose up -d --build

# Verify healthy status across all 4 services
docker compose ps
```

### Step 4: Validate Service Health
```bash
curl -I https://api.opdownloader.app/api/v1/health
```
Expected output:
```http
HTTP/2 200 
strict-transport-security: max-age=31536000; includeSubDomains; preload
x-content-type-options: nosniff
x-frame-options: DENY
content-type: application/json; charset=utf-8
```

---

## 4. Scaling Download Workers

To scale download processing concurrency independently of the API gateway:
```bash
docker compose up -d --scale worker=4
```

---

## 5. Automated Backup & Disaster Recovery

* **PostgreSQL Backup Script (`/opt/scripts/backup_db.sh`):**
```bash
#!/bin/bash
BACKUP_DIR="/var/backups/op_postgres"
mkdir -p "$BACKUP_DIR"
docker exec op-downloader-postgres pg_dump -U op_app_user op_downloader | gzip > "$BACKUP_DIR/db_$(date +%Y%m%d_%H%M%S).sql.gz"
find "$BACKUP_DIR" -type f -mtime +14 -delete
```
* **Restore Procedure:**
```bash
gunzip -c /var/backups/op_postgres/db_target.sql.gz | docker exec -i op-downloader-postgres psql -U op_app_user -d op_downloader
```
