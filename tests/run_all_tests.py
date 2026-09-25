import subprocess
import sys
import os

TEST_FILES = [
    "tests/test_validation_parity.py",
    "tests/test_backend_core.py",
    "tests/test_db_and_redis.py",
    "tests/test_providers.py",
    "tests/test_resumable_streaming.py",
    "tests/test_security_hardening.py",
    "tests/test_security_penetration.py",
    "tests/test_e2e_all_cases.py"
]

def main():
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    python_bin = sys.executable

    print("=" * 70)
    print(" OP DOWNLOADER — MASTER VERIFICATION TEST RUNNER")
    print("=" * 70)

    total_passed = 0
    total_failed = 0

    for test_file in TEST_FILES:
        full_path = os.path.join(root_dir, test_file)
        print(f"\n▶ Executing: {test_file} ...")
        res = subprocess.run([python_bin, full_path], cwd=root_dir, capture_output=True, text=True)
        if res.returncode == 0:
            print(res.stdout.strip())
            print(f"✓ PASS: {test_file}")
            total_passed += 1
        else:
            print(f"✗ FAIL: {test_file}")
            print(res.stderr)
            print(res.stdout)
            total_failed += 1

    print("\n" + "=" * 70)
    print(f" TEST RUN SUMMARY: {total_passed} PASSED, {total_failed} FAILED")
    print("=" * 70)

    if total_failed > 0:
        sys.exit(1)
    else:
        print("ALL SUITES VERIFIED AND READY FOR PHASE 10 RELEASE!")

if __name__ == "__main__":
    main()
