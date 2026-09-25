import os
import re

ROOT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

def test_network_security_config_disallows_cleartext():
    nsc_path = os.path.join(ROOT_DIR, "android", "app", "src", "main", "res", "xml", "network_security_config.xml")
    assert os.path.exists(nsc_path), "Network security config file must exist"
    with open(nsc_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert 'cleartextTrafficPermitted="false"' in content, "Cleartext traffic must be explicitly prohibited"
    print("✓ Network Security Config disallows cleartext traffic.")

def test_proguard_rules_strip_logs_and_protect_keystore():
    pg_path = os.path.join(ROOT_DIR, "android", "app", "proguard-rules.pro")
    assert os.path.exists(pg_path), "ProGuard rules file must exist"
    with open(pg_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "-assumenosideeffects class android.util.Log" in content, "Logs must be stripped in release builds"
    assert "KeystoreManager" in content, "Keystore manager must be protected from reflection stripping"
    print("✓ ProGuard rules strip debug logs and preserve Keystore.")

def test_nginx_security_headers_and_limits():
    nginx_conf = os.path.join(ROOT_DIR, "infrastructure", "nginx", "conf.d", "op_downloader.conf")
    assert os.path.exists(nginx_conf), "Nginx server configuration must exist"
    with open(nginx_conf, "r", encoding="utf-8") as f:
        content = f.read()
    assert "Strict-Transport-Security" in content, "HSTS header must be configured"
    assert "X-Content-Type-Options" in content, "nosniff header must be configured"
    assert "client_max_body_size 32k;" in content, "Payload limit of 32k must be enforced"
    print("✓ Nginx enforces HSTS, security headers, and request body limits.")

def test_docker_sandbox_isolation():
    compose_path = os.path.join(ROOT_DIR, "docker-compose.yml")
    assert os.path.exists(compose_path), "docker-compose.yml must exist"
    with open(compose_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "user: \"10001:10001\"" in content, "Worker must run as unprivileged non-root user"
    assert "cap_drop:" in content and "ALL" in content, "Worker must drop all Linux capabilities"
    assert "read_only: true" in content, "Worker must run with read-only root filesystem"
    print("✓ Docker container sandbox isolation (non-root, cap-drop, read-only) verified.")

if __name__ == "__main__":
    test_network_security_config_disallows_cleartext()
    test_proguard_rules_strip_logs_and_protect_keystore()
    test_nginx_security_headers_and_limits()
    test_docker_sandbox_isolation()
    print("\nALL SECURITY HARDENING VERIFICATION TESTS PASSED SUCCESSFULLY!")
