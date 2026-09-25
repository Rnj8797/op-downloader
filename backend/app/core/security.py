import hashlib
import hmac
import time
import json
import base64
import os
from typing import Optional, Dict, Any, Set
from app.core.config import settings

# In-memory revocation set (synced with Redis in production)
REVOKED_TOKENS: Set[str] = set()

# Initialize Argon2id password hasher if available
try:
    from argon2 import PasswordHasher
    from argon2.exceptions import VerifyMismatchError
    ph = PasswordHasher(time_cost=3, memory_cost=65536, parallelism=4, hash_len=32)
    USE_ARGON2 = True
except ImportError:
    USE_ARGON2 = False

def hash_password(password: str) -> str:
    """
    Hashes password using Argon2id with unique cryptographic salt,
    or salted PBKDF2-HMAC-SHA256 with random 16-byte salt fallback.
    """
    if USE_ARGON2:
        return ph.hash(password)
    else:
        salt = os.urandom(16)
        key = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, 100000)
        return f"$pbkdf2-sha256$100000${salt.hex()}${key.hex()}"

def verify_password(plain_password: str, hashed_password: str) -> bool:
    """
    Verifies plain password against hashed password string without timing leakage.
    """
    if not plain_password or not hashed_password:
        return False

    if USE_ARGON2 and hashed_password.startswith("$argon2"):
        try:
            return ph.verify(hashed_password, plain_password)
        except Exception:
            return False
    elif hashed_password.startswith("$pbkdf2-sha256$"):
        try:
            parts = hashed_password.split("$")
            iterations = int(parts[2])
            salt = bytes.fromhex(parts[3])
            expected_key = parts[4]
            key = hashlib.pbkdf2_hmac("sha256", plain_password.encode("utf-8"), salt, iterations)
            return hmac.compare_digest(key.hex(), expected_key)
        except Exception:
            return False
    else:
        # Legacy fallback comparison
        salt = "op_salt_fixed_bytes_32"
        key = hashlib.pbkdf2_hmac("sha256", plain_password.encode("utf-8"), salt.encode("utf-8"), 100000)
        return hmac.compare_digest(key.hex(), hashed_password)

def create_access_token(
    subject: str,
    role: str = "USER",
    expires_minutes: int = 15,
    issuer: str = "op-downloader",
    audience: str = "op-client"
) -> str:
    """Creates a signed, time-limited JWT access token."""
    header = {"alg": "HS256", "typ": "JWT"}
    now = int(time.time())
    payload = {
        "sub": subject,
        "role": role,
        "iss": issuer,
        "aud": audience,
        "iat": now,
        "exp": now + (expires_minutes * 60),
        "jti": os.urandom(16).hex()
    }
    
    header_b64 = base64.urlsafe_b64encode(json.dumps(header).encode()).decode().rstrip("=")
    payload_b64 = base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip("=")
    
    signing_input = f"{header_b64}.{payload_b64}"
    signature = hmac.new(
        settings.JWT_SECRET.encode(),
        signing_input.encode(),
        hashlib.sha256
    ).digest()
    sig_b64 = base64.urlsafe_b64encode(signature).decode().rstrip("=")
    
    return f"{signing_input}.{sig_b64}"

def revoke_token(token: str):
    """Adds a token's JTI to the revocation list."""
    payload = verify_token(token)
    if payload and "jti" in payload:
        REVOKED_TOKENS.add(payload["jti"])

def verify_token(
    token: str,
    expected_issuer: str = "op-downloader",
    expected_audience: str = "op-client"
) -> Optional[Dict[str, Any]]:
    """
    Verifies HMAC signature, algorithm, expiration, issuer, audience, and revocation status.
    Strictly rejects 'none' algorithm, asymmetric forgery, and missing claims.
    """
    if not token or not isinstance(token, str):
        return None

    try:
        parts = token.split(".")
        if len(parts) != 3:
            return None

        # Verify Header & Algorithm
        header_padding = "=" * ((4 - len(parts[0]) % 4) % 4)
        header = json.loads(base64.urlsafe_b64decode(parts[0] + header_padding).decode())
        
        # Enforce strict HS256 (reject 'none', 'RS256', etc.)
        if header.get("alg") != "HS256":
            return None

        signing_input = f"{parts[0]}.{parts[1]}"
        expected_sig = hmac.new(
            settings.JWT_SECRET.encode(),
            signing_input.encode(),
            hashlib.sha256
        ).digest()

        sig_padding = "=" * ((4 - len(parts[2]) % 4) % 4)
        received_sig = base64.urlsafe_b64decode(parts[2] + sig_padding)

        if not hmac.compare_digest(expected_sig, received_sig):
            return None

        payload_padding = "=" * ((4 - len(parts[1]) % 4) % 4)
        payload = json.loads(base64.urlsafe_b64decode(parts[1] + payload_padding).decode())

        # Validate mandatory claims
        required_claims = ["sub", "role", "iat", "exp"]
        if not all(k in payload for k in required_claims):
            return None

        # Check expiration
        now = int(time.time())
        if payload.get("exp", 0) < now:
            return None

        # Check issuer & audience if present
        if "iss" in payload and payload["iss"] != expected_issuer:
            return None
        if "aud" in payload and payload["aud"] != expected_audience:
            return None

        # Check revocation
        if payload.get("jti") in REVOKED_TOKENS:
            return None

        return payload
    except Exception:
        return None
