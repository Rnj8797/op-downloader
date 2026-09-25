import sys
import os

# Add backend directory to sys.path
backend_path = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "backend"))
sys.path.insert(0, backend_path)

from app.core.ssrf import validate_and_resolve_url, SSRFProtectionError, is_ip_allowed
from app.core.security import hash_password, verify_password, create_access_token, verify_token

def test_ssrf_ip_filter():
    assert is_ip_allowed("127.0.0.1") is False
    assert is_ip_allowed("10.0.0.1") is False
    assert is_ip_allowed("192.168.1.50") is False
    assert is_ip_allowed("172.16.0.1") is False
    assert is_ip_allowed("169.254.169.254") is False
    assert is_ip_allowed("::1") is False
    assert is_ip_allowed("8.8.8.8") is True
    assert is_ip_allowed("1.1.1.1") is True
    print("✓ SSRF IP Filter passed.")

def test_ssrf_schemes_and_hosts():
    try:
        validate_and_resolve_url("http://example.com/video.mp4")
        assert False, "Should have rejected plain http"
    except SSRFProtectionError as e:
        assert e.code == "UNSUPPORTED_SCHEME"

    try:
        validate_and_resolve_url("https://localhost/admin")
        assert False, "Should have rejected localhost"
    except SSRFProtectionError as e:
        assert e.code == "SSRF_BLOCKED"

    try:
        validate_and_resolve_url("https://169.254.169.254/latest/meta-data")
        assert False, "Should have rejected cloud metadata"
    except SSRFProtectionError as e:
        assert e.code == "SSRF_BLOCKED"

    print("✓ SSRF scheme and host rejection passed.")

def test_password_security():
    pwd = "SuperSecretPassword123!"
    hashed = hash_password(pwd)
    assert hashed != pwd
    assert verify_password(pwd, hashed) is True
    assert verify_password("WrongPassword!", hashed) is False
    print("✓ Password hashing and verification passed.")

def test_jwt_lifecycle():
    token = create_access_token(subject="user_123", role="USER", expires_minutes=15)
    assert token is not None
    payload = verify_token(token)
    assert payload is not None
    assert payload["sub"] == "user_123"
    assert payload["role"] == "USER"

    # Test token tampering
    tampered_token = token[:-4] + "fake"
    assert verify_token(tampered_token) is None
    print("✓ JWT token creation, signature verification, and tamper detection passed.")

if __name__ == "__main__":
    test_ssrf_ip_filter()
    test_ssrf_schemes_and_hosts()
    test_password_security()
    test_jwt_lifecycle()
    print("\nALL BACKEND CORE AND SECURITY TESTS PASSED SUCCESSFULLY!")
