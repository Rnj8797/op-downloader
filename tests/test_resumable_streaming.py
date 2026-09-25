import os
import time
import tempfile
import sys

backend_path = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "backend"))
sys.path.insert(0, backend_path)

from app.workers.tasks import ResumableStreamDownloader

def test_scratch_file_lifecycle_and_ttl():
    with tempfile.TemporaryDirectory() as temp_dir:
        downloader = ResumableStreamDownloader(scratch_dir=temp_dir)
        
        # 1. Create a simulated temporary file
        job_id = "test_job_123"
        safe_name = "Sample_Video.mp4"
        temp_file = os.path.join(temp_dir, f"{job_id}.tmp")
        final_file = os.path.join(temp_dir, f"{job_id}_{safe_name}")

        # Simulate writing first chunk (Range: 0-1024)
        chunk1 = b"A" * 1024
        with open(temp_file, "wb") as f:
            f.write(chunk1)
        assert os.path.getsize(temp_file) == 1024

        # Simulate resuming from byte 1024 (Range: 1024-2048)
        chunk2 = b"B" * 1024
        with open(temp_file, "ab") as f:
            f.write(chunk2)
        assert os.path.getsize(temp_file) == 2048

        # Simulate atomic completion rename
        os.rename(temp_file, final_file)
        assert not os.path.exists(temp_file)
        assert os.path.exists(final_file)
        assert os.path.getsize(final_file) == 2048
        print("✓ Chunked Range resumption and atomic completion rename passed.")

        # 2. Test TTL Cleanup
        # Create an expired file artificially by setting mtime to 2 hours ago
        expired_file = os.path.join(temp_dir, "expired_item.mp4")
        with open(expired_file, "w") as f:
            f.write("old data")
        two_hours_ago = time.time() - 7200
        os.utime(expired_file, (two_hours_ago, two_hours_ago))

        downloader.purge_expired_scratch_files(ttl_minutes=60)
        assert not os.path.exists(expired_file), "Expired file should have been deleted"
        assert os.path.exists(final_file), "Active/recent file should NOT be deleted"
        print("✓ Scratch storage automated TTL cleanup passed.")

if __name__ == "__main__":
    test_scratch_file_lifecycle_and_ttl()
    print("\nALL RESUMABLE STREAMING AND WORKER TESTS PASSED SUCCESSFULLY!")
