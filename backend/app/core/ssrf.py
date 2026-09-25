import socket
import ipaddress
from urllib.parse import urlparse, unquote
from typing import Tuple, Optional, List
import httpx

# Comprehensive list of blocked CIDRs (RFC 1918, RFC 3927, CGNAT, Loopback, Cloud Metadata, etc.)
BLOCKED_NETWORKS = [
    ipaddress.ip_network("0.0.0.0/8"),
    ipaddress.ip_network("10.0.0.0/8"),
    ipaddress.ip_network("100.64.0.0/10"),     # CGNAT
    ipaddress.ip_network("127.0.0.0/8"),       # IPv4 Loopback
    ipaddress.ip_network("169.254.0.0/16"),    # Link-local & Cloud Metadata (AWS/GCP/Azure)
    ipaddress.ip_network("172.16.0.0/12"),     # RFC 1918 Private
    ipaddress.ip_network("192.0.0.0/24"),      # IETF Protocol Assignments
    ipaddress.ip_network("192.0.2.0/24"),      # TEST-NET-1 Documentation
    ipaddress.ip_network("192.88.99.0/24"),    # 6to4 Relay Anycast
    ipaddress.ip_network("192.168.0.0/16"),    # RFC 1918 Private
    ipaddress.ip_network("198.18.0.0/15"),     # Network Interconnect Benchmarking
    ipaddress.ip_network("198.51.100.0/24"),   # TEST-NET-2 Documentation
    ipaddress.ip_network("203.0.113.0/24"),    # TEST-NET-3 Documentation
    ipaddress.ip_network("224.0.0.0/4"),       # Multicast
    ipaddress.ip_network("240.0.0.0/4"),       # Reserved / Class E
    ipaddress.ip_network("255.255.255.255/32"),# Broadcast
    # IPv6 ranges
    ipaddress.ip_network("::/128"),            # Unspecified
    ipaddress.ip_network("::1/128"),          # IPv6 Loopback
    ipaddress.ip_network("::ffff:0:0/96"),     # IPv4-mapped IPv6
    ipaddress.ip_network("64:ff9b::/96"),      # IPv4/IPv6 translation
    ipaddress.ip_network("100::/64"),          # Discard prefix
    ipaddress.ip_network("2001:db8::/32"),     # Documentation
    ipaddress.ip_network("fc00::/7"),          # Unique Local Addresses (ULA)
    ipaddress.ip_network("fe80::/10"),         # Link-local Unicast
    ipaddress.ip_network("ff00::/8"),          # Multicast
]

# Additional specific cloud metadata IP endpoints
SPECIFIC_BLOCKED_IPS = {
    "169.254.169.254", # AWS/GCP/Azure metadata
    "169.254.169.123",
    "100.100.100.200", # Alibaba Cloud metadata
}

# Permitted HTTP/HTTPS ports for public media fetching
ALLOWED_PORTS = {80, 443, 8443}

class SSRFProtectionError(Exception):
    def __init__(self, message: str, code: str = "SSRF_BLOCKED"):
        self.message = message
        self.code = code
        super().__init__(self.message)

def parse_special_ip_representations(hostname: str) -> Optional[ipaddress.IPv4Address | ipaddress.IPv6Address]:
    """
    Handles decimal integer IPs (e.g. 2130706433 -> 127.0.0.1),
    hex IPs (0x7f000001), octal IPs (0177.0.0.1), and bracketed IPv6.
    """
    clean = hostname.strip("[]").strip()
    
    # Check if raw integer / hex string
    if clean.isdigit() or (clean.lower().startswith("0x") and len(clean) > 2):
        try:
            val = int(clean, 0)
            if 0 <= val <= 0xFFFFFFFF:
                return ipaddress.IPv4Address(val)
        except ValueError:
            pass

    # Standard IP address parse
    try:
        return ipaddress.ip_address(clean)
    except ValueError:
        pass

    return None

def is_ip_allowed(ip_obj: ipaddress.IPv4Address | ipaddress.IPv6Address | str) -> bool:
    """
    Verifies that an IP is globally routable and not in private, loopback,
    documentation, multicast, or cloud metadata ranges.
    Unpacks IPv4-mapped IPv6 addresses for dual validation.
    """
    if isinstance(ip_obj, str):
        parsed_ip = parse_special_ip_representations(ip_obj)
        if parsed_ip is None:
            return False
        ip = parsed_ip
    else:
        ip = ipobj if (ipobj := ip_obj) else ipaddress.ip_address(str(ip_obj))

    # Check against specific metadata endpoints
    if str(ip) in SPECIFIC_BLOCKED_IPS:
        return False

    # Handle IPv4-mapped IPv6 (e.g. ::ffff:127.0.0.1 or ::ffff:169.254.169.254)
    if isinstance(ip, ipaddress.IPv6Address) and ip.ipv4_mapped:
        ipv4_underlying = ip.ipv4_mapped
        if not is_ip_allowed(ipv4_underlying):
            return False

    for network in BLOCKED_NETWORKS:
        if ip in network:
            return False

    return not (ip.is_private or ip.is_loopback or ip.is_link_local or 
                ip.is_multicast or ip.is_reserved or ip.is_unspecified)

def validate_and_resolve_url(url: str) -> Tuple[str, str]:
    """
    Validates URL syntax, enforces HTTPS scheme, validates port,
    normalizes hostnames, resolves DNS pre-connection, and verifies against all SSRF filters.
    Returns (normalized_url, resolved_ip).
    """
    if not url or not url.strip():
        raise SSRFProtectionError("URL cannot be empty.", "INVALID_URL")

    trimmed = unquote(url.strip())
    try:
        parsed = urlparse(trimmed)
        hostname = parsed.hostname
        port = parsed.port or (443 if parsed.scheme.lower() == "https" else 80)
    except ValueError:
        raise SSRFProtectionError("Malformed URL syntax or port.", "MALFORMED_URL")
    except Exception:
        raise SSRFProtectionError("Malformed URL syntax.", "MALFORMED_URL")

    # 1. Enforce HTTPS scheme (reject http, file, gopher, ftp, javascript, etc.)
    scheme = parsed.scheme.lower()
    if scheme != "https":
        raise SSRFProtectionError("Only secure HTTPS links are supported.", "UNSUPPORTED_SCHEME")

    if not hostname:
        raise SSRFProtectionError("Missing hostname in URL.", "INVALID_HOSTNAME")

    # Strip trailing dot from FQDN
    clean_host = hostname.rstrip(".").lower()

    # 2. Port Whitelist Check
    if port not in ALLOWED_PORTS:
        raise SSRFProtectionError(f"Port {port} is not permitted for media fetching.", "INVALID_PORT")

    # 3. Direct string rejection of obvious loopback keywords
    if clean_host in ("localhost", "127.0.0.1", "0.0.0.0", "::1", "0"):
        raise SSRFProtectionError("Access to local addresses is prohibited.", "SSRF_BLOCKED")

    # 4. Check if host is direct IP representation (decimal, hex, octal, IPv6)
    direct_ip = parse_special_ip_representations(clean_host)
    if direct_ip is not None:
        if not is_ip_allowed(direct_ip):
            raise SSRFProtectionError("Access to private or metadata IP addresses is prohibited.", "SSRF_BLOCKED")
        return trimmed, str(direct_ip)

    # 5. DNS Pre-Resolution Check
    try:
        addr_info = socket.getaddrinfo(clean_host, port, proto=socket.IPPROTO_TCP)
        if not addr_info:
            raise SSRFProtectionError("Could not resolve host.", "DNS_RESOLUTION_FAILED")

        resolved_ip = None
        for entry in addr_info:
            ip_str = entry[4][0]
            if not is_ip_allowed(ip_str):
                raise SSRFProtectionError(f"Restricted IP address resolved: {ip_str}", "SSRF_BLOCKED")
            if resolved_ip is None:
                resolved_ip = ip_str

        return trimmed, resolved_ip
    except socket.gaierror:
        raise SSRFProtectionError("Could not resolve host DNS.", "DNS_RESOLUTION_FAILED")

async def safe_fetch_with_redirect_pinching(
    client: httpx.AsyncClient,
    initial_url: str,
    max_redirects: int = 5,
    method: str = "GET",
    headers: Optional[dict] = None
) -> httpx.Response:
    """
    Performs HTTP requests with manual redirect verification.
    Intercepts every 3xx redirect and re-validates the destination URL against SSRF filters
    before following the next hop.
    """
    current_url = initial_url
    redirect_count = 0

    req_headers = {"User-Agent": "OP-Downloader-Security/1.0"}
    if headers:
        req_headers.update(headers)

    while True:
        # Validate current hop
        normalized_url, _ = validate_and_resolve_url(current_url)

        response = await client.request(
            method=method,
            url=normalized_url,
            headers=req_headers,
            follow_redirects=False
        )

        if response.is_redirect:
            redirect_count += 1
            if redirect_count > max_redirects:
                raise SSRFProtectionError("Too many redirects.", "REDIRECT_LOOP")

            location = response.headers.get("location")
            if not location:
                raise SSRFProtectionError("Redirect response missing Location header.", "MALFORMED_REDIRECT")

            # Handle relative redirect URLs
            if location.startswith("/"):
                parsed_current = urlparse(current_url)
                current_url = f"{parsed_current.scheme}://{parsed_current.netloc}{location}"
            else:
                current_url = location
            continue

        return response
