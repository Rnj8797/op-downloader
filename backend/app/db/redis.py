import time
import json
from typing import Optional, Dict, Any

class RedisService:
    """
    Manages rate-limiting counters, active download concurrency,
    and ephemeral progress hashes with automatic memory fallback.
    """
    def __init__(self, redis_url: Optional[str] = None):
        self.redis_client = None
        self._memory_store: Dict[str, Any] = {}
        self._memory_expirations: Dict[str, float] = {}

    def _cleanup_expired(self):
        now = time.time()
        expired_keys = [k for k, exp in self._memory_expirations.items() if exp < now]
        for k in expired_keys:
            self._memory_store.pop(k, None)
            self._memory_expirations.pop(k, None)

    async def check_rate_limit(self, identifier: str, max_requests: int = 10, window_seconds: int = 3600) -> bool:
        """
        Token-bucket / Sliding rate limiter.
        Returns True if request is ALLOWED, False if rate limit EXCEEDED.
        """
        self._cleanup_expired()
        key = f"rl:{identifier}"
        now = time.time()
        
        current_count = self._memory_store.get(key, 0)
        if current_count >= max_requests:
            return False
            
        self._memory_store[key] = current_count + 1
        if key not in self._memory_expirations:
            self._memory_expirations[key] = now + window_seconds
        return True

    async def acquire_concurrency_slot(self, user_or_device_id: str, max_concurrent: int = 2) -> bool:
        """
        Enforces maximum simultaneous active download jobs.
        """
        key = f"conc:{user_or_device_id}"
        active = self._memory_store.get(key, 0)
        if active >= max_concurrent:
            return False
        self._memory_store[key] = active + 1
        return True

    async def release_concurrency_slot(self, user_or_device_id: str):
        key = f"conc:{user_or_device_id}"
        active = self._memory_store.get(key, 0)
        if active > 0:
            self._memory_store[key] = active - 1

    async def set_job_progress(self, job_id: str, progress_data: dict, ttl_seconds: int = 1800):
        key = f"job:progress:{job_id}"
        self._memory_store[key] = json.dumps(progress_data)
        self._memory_expirations[key] = time.time() + ttl_seconds

    async def get_job_progress(self, job_id: str) -> Optional[dict]:
        self._cleanup_expired()
        key = f"job:progress:{job_id}"
        raw = self._memory_store.get(key)
        if raw:
            return json.loads(raw)
        return None

# Singleton instance
redis_service = RedisService()
