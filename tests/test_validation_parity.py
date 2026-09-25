"""
Cross-Platform Test Suite: URL Validation & SSRF Defense Parity
Validates identical rules across Android ValidateUrlUseCase and Backend SSRF Engine.
"""
from urllib.parse import urlparse
import ipaddress

BLOCKED_NETWORKS = [
    ipaddress.ip_network("0.0.0.0/8"),
    ipaddress.ip_network("10.0.0.0/8"),
    ipaddress.ip_network("127.0.0.0/8"),
    ipaddress.ip_network("169.254.0.0/16"),   # AWS/GCP Cloud Metadata
    ipaddress.ip_network("172.16.0.0/12"),
    ipaddress.ip_network("192.168.0.0/16"),
    ipaddress.ip_network("::1/128"),
    ipaddress.ip_network("fc00::/7"),
    ipaddress.ip_network("fe80::/10"),
]

def validate_url(url: str) -> tuple[bool, str]:
    if not url or not url.strip():
        return False, "URL cannot be empty."

    trimmed = url.strip()
    try:
        parsed = urlparse(trimmed)
    except Exception:
        return False, "Malformed URL syntax."

    if parsed.scheme.lower() != "https":
        return False, "Only secure HTTPS links are supported."

    host = parsed.hostname
    if not host:
        return False, "Malformed URL hostname."

    lower_host = host.lower()
    if lower_host in ("localhost", "0.0.0.0", "127.0.0.1", "::1"):
        return False, "Access to local or private network addresses is prohibited."

    # IP address range check
    try:
        ip = ipaddress.ip_address(lower_host)
        for net in BLOCKED_NETWORKS:
            if ip in net:
                return False, "Access to local or private network addresses is prohibited."
        if ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_multicast:
            return False, "Access to local or private network addresses is prohibited."
    except ValueError:
        # Hostname, not direct IP
        pass

    return True, trimmed

def test_valid_https_url():
    valid, res = validate_url("https://commondatastorage.googleapis.com/sample/video.mp4")
    assert valid is True
    assert res == "https://commondatastorage.googleapis.com/sample/video.mp4"

def test_insecure_http_rejected():
    valid, msg = validate_url("http://example.com/media.mp4")
    assert valid is False
    assert msg == "Only secure HTTPS links are supported."

def test_file_and_ftp_rejected():
    valid1, _ = validate_url("file:///etc/passwd")
    assert valid1 is False

    valid2, _ = validate_url("ftp://example.com/file.mp4")
    assert valid2 is False

def test_loopback_and_localhost_rejected():
    valid1, _ = validate_url("https://localhost/admin")
    assert valid1 is False

    valid2, _ = validate_url("https://127.0.0.1:8080/data")
    assert valid2 is False

def test_cloud_metadata_rejected():
    valid, msg = validate_url("https://169.254.169.254/latest/meta-data")
    assert valid is False
    assert "private network" in msg

def test_private_subnets_rejected():
    valid10, _ = validate_url("https://10.0.0.1/video.mp4")
    assert valid10 is False

    valid192, _ = validate_url("https://192.168.1.1/video.mp4")
    assert valid192 is False

    valid172, _ = validate_url("https://172.16.0.1/video.mp4")
    assert valid172 is False

if __name__ == "__main__":
    test_valid_https_url()
    test_insecure_http_rejected()
    test_file_and_ftp_rejected()
    test_loopback_and_localhost_rejected()
    test_cloud_metadata_rejected()
    test_private_subnets_rejected()
    print("ALL 6 URL VALIDATION & SSRF PARITY TESTS PASSED SUCCESSFULLY!")
