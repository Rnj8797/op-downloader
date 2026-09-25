import asyncio
import sys
import os

backend_path = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "backend"))
sys.path.insert(0, backend_path)

from app.db.redis import RedisService
from app.db.models import User, Device, DownloadJob, DownloadItem

async def run_redis_tests():
    redis = RedisService()
    
    # 1. Rate Limiting Tests (max 5 for test)
    for i in range(5):
        allowed = await redis.check_rate_limit("test_ip_1", max_requests=5, window_seconds=60)
        assert allowed is True, f"Request {i+1} should have been allowed"
        
    blocked = await redis.check_rate_limit("test_ip_1", max_requests=5, window_seconds=60)
    assert blocked is False, "Request 6 should have been rate limited"
    print("✓ Redis Rate Limiting test passed.")

    # 2. Concurrency Limit Tests (max 2 active)
    slot1 = await redis.acquire_concurrency_slot("device_abc", max_concurrent=2)
    assert slot1 is True
    slot2 = await redis.acquire_concurrency_slot("device_abc", max_concurrent=2)
    assert slot2 is True
    slot3 = await redis.acquire_concurrency_slot("device_abc", max_concurrent=2)
    assert slot3 is False, "Slot 3 should exceed concurrency limit of 2"

    await redis.release_concurrency_slot("device_abc")
    slot3_retry = await redis.acquire_concurrency_slot("device_abc", max_concurrent=2)
    assert slot3_retry is True, "Slot should be available after release"
    print("✓ Redis Download Concurrency Limiter test passed.")

    # 3. Progress Tracking
    await redis.set_job_progress("job_99", {"pct": 68.4, "speed": 8808038})
    progress = await redis.get_job_progress("job_99")
    assert progress is not None
    assert progress["pct"] == 68.4
    assert progress["speed"] == 8808038
    print("✓ Redis Progress Cache test passed.")

def test_db_models():
    user = User(email="test@opdownloader.app", role="USER")
    assert user.email == "test@opdownloader.app"
    assert user.role == "USER"

    job = DownloadJob(
        device_id="dev_1",
        source_url_hash="e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        provider="DIRECT_MEDIA",
        media_type="VIDEO",
        status="QUEUED"
    )
    assert job.status == "QUEUED"
    assert job.provider == "DIRECT_MEDIA"
    print("✓ Database models initialization test passed.")

if __name__ == "__main__":
    test_db_models()
    asyncio.run(run_redis_tests())
    print("\nALL DATABASE AND REDIS TESTS PASSED SUCCESSFULLY!")
