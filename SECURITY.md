# Security Policy - OP Downloader

## 1. Supported Versions

We provide security updates and patches for the following versions of OP Downloader:

| Version | Supported | Security Maintenance |
| :--- | :--- | :--- |
| `1.0.x` | :white_check_mark: | Active support |
| `< 1.0.0` | :x: | End of Life |

---

## 2. Reporting a Vulnerability

The OP Downloader team takes the security of our application, our users, and third-party platforms very seriously. If you identify a security vulnerability, we appreciate your help in disclosing it to us responsibly.

### How to Report
* **Email:** Send your report to `security@opdownloader.app` with the subject `[VULNERABILITY] OP Downloader - <Brief Description>`.
* **PGP Key:** For encrypted communication, please request our public PGP key.
* **Information to Include:**
  - Clear description of the vulnerability.
  - Type of issue (e.g., SSRF, Path Traversal, Authentication Bypass, Memory Leak).
  - Step-by-step reproduction steps or proof-of-concept (PoC).
  - Potential impact and affected components (Android App, Backend API, Worker Sandbox).

### Our Commitment
* We will acknowledge receipt of your vulnerability report within **48 hours**.
* We will provide a triage assessment and estimated remediation timeline within **5 business days**.
* We will notify you once the fix has been implemented and tested.
* We ask that you maintain confidentiality and not disclose the issue publicly until a patch has been released.

---

## 3. Security Architecture & Boundary

* **No Circumvention Guarantee:** OP Downloader does not and will never build features or exploit mechanisms designed to bypass DRM (Widevine, FairPlay), paywalls, CAPTCHA, private account restrictions, or platform access controls. Reports highlighting inability to download private or DRM-locked content will be closed as intended behavior.
* **SSRF Defense:** The backend utilizes pre-connection DNS validation, private IP address blacklisting, and redirect pinching.
* **Isolated Execution:** Download workers operate in non-root, capability-stripped container sandboxes with read-only filesystems and ephemeral storage.
* **Client Encryption:** Android client secrets and tokens are secured via Android Keystore backed `EncryptedSharedPreferences`.

---

## 4. Dependency Update Policy

* Third-party libraries in Android (Gradle) and Backend (Python) are automatically monitored via Dependabot and Snyk.
* Critical and high-severity CVEs are patched and released within **72 hours** of public disclosure.
* Regular dependency upgrades occur on a bi-weekly cycle.
