import os
import time
import httpx
import re
import asyncio
from typing import Optional, Callable
from app.core.config import settings
from app.core.ssrf import validate_and_resolve_url, SSRFProtectionError

# Windows reserved device names that must not be created on filesystem
WINDOWS_RESERVED_NAMES = {
    "CON", "PRN", "AUX", "NUL",
    "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
    "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
}

class OversizedMediaException(Exception):
    pass

class ResumableStreamDownloader:
    """
    Worker task that performs streaming chunked downloads with HTTP Range resumption,
    strict memory boundaries (64KB buffer), 2GB file size enforcement,
    and sanitized file handling.
    """
    def __init__(self, scratch_dir: Optional[str] = None):
        self.scratch_dir = scratch_dir or settings.SCRATCH_STORAGE_DIR
        os.makedirs(self.scratch_dir, exist_ok=True)

    def sanitize_filename(self, filename: str) -> str:
        """
        Strips path traversal, null bytes, control characters, and reserved device names.
        """
        # Remove path separators and null bytes
        clean = filename.replace("/", "_").replace("\\", "_").replace("\x00", "")
        # Whitelist alphanumeric + safe punctuation
        clean = re.sub(r"[^a-zA-Z0-9._-]", "_", clean)
        # Collapse multiple dots to prevent ../
        clean = re.sub(r"\.\.+", "_", clean)
        
        # Check base name against Windows reserved device names
        base_name = clean.split(".")[0].upper()
        if base_name in WINDOWS_RESERVED_NAMES:
            clean = f"OP_{clean}"

        # Limit length to 128 characters
        if len(clean) > 128:
            ext = clean.split(".")[-1] if "." in clean else "mp4"
            clean = f"{clean[:120]}.{ext}"

        return clean or "OP_Media_File.mp4"

    async def download_file(
        self,
        job_id: str,
        target_url: str,
        safe_filename: str,
        max_bytes: int = settings.MAX_DOWNLOAD_SIZE_BYTES,
        progress_callback: Optional[Callable[[int, int, float], None]] = None
    ) -> str:
        # Pre-verify target URL with SSRF engine
        validate_and_resolve_url(target_url)

        sanitized_name = self.sanitize_filename(safe_filename)
        temp_path = os.path.join(self.scratch_dir, f"{job_id}.tmp")
        final_path = os.path.join(self.scratch_dir, f"{job_id}_{sanitized_name}")

        existing_bytes = os.path.getsize(temp_path) if os.path.exists(temp_path) else 0
        headers = {"User-Agent": "OP-Downloader-Worker/1.0"}

        if existing_bytes > 0:
            headers["Range"] = f"bytes={existing_bytes}-"

        timeout_cfg = httpx.Timeout(connect=15.0, read=30.0, write=15.0, pool=10.0)

        async with httpx.AsyncClient(timeout=timeout_cfg, follow_redirects=False) as client:
            async with client.stream("GET", target_url, headers=headers) as response:
                if response.status_code not in (200, 206):
                    if response.status_code == 416: # Range not satisfiable, reset
                        if os.path.exists(temp_path):
                            os.remove(temp_path)
                        existing_bytes = 0
                    else:
                        raise RuntimeError(f"Download failed with status {response.status_code}")

                try:
                    cl_header = response.headers.get("content-length")
                    content_len = int(cl_header) if cl_header and int(cl_header) > 0 else 0
                except (ValueError, TypeError):
                    content_len = 0

                total_bytes = existing_bytes + content_len if content_len > 0 else existing_bytes
                
                # Check advertised Content-Length against maximum permitted size
                if total_bytes > max_bytes:
                    if os.path.exists(temp_path):
                        os.remove(temp_path)
                    raise OversizedMediaException(f"Media exceeds maximum allowed size of {max_bytes} bytes.")

                downloaded_bytes = existing_bytes

                # Open temp file in append binary mode
                mode = "ab" if existing_bytes > 0 else "wb"
                with open(temp_path, mode) as f:
                    async for chunk in response.aiter_bytes(chunk_size=65536):
                        f.write(chunk)
                        downloaded_bytes += len(chunk)

                        # Enforce hard runtime limit against infinite streams
                        if downloaded_bytes > max_bytes:
                            f.close()
                            if os.path.exists(temp_path):
                                os.remove(temp_path)
                            raise OversizedMediaException(f"Stream exceeded maximum limit of {max_bytes} bytes.")

                        if progress_callback and total_bytes > 0:
                            pct = round((downloaded_bytes / total_bytes) * 100, 1)
                            progress_callback(downloaded_bytes, total_bytes, pct)

        # Atomic rename once complete
        if os.path.exists(final_path):
            os.remove(final_path)
        os.rename(temp_path, final_path)

        # Ensure file permissions are non-executable (0644)
        try:
            os.chmod(final_path, 0o644)
        except OSError:
            pass

        return final_path

    def purge_expired_scratch_files(self, ttl_minutes: int = 60):
        """Scans scratch directory and purges files older than TTL."""
        now = time.time()
        ttl_seconds = ttl_minutes * 60
        for fname in os.listdir(self.scratch_dir):
            fpath = os.path.join(self.scratch_dir, fname)
            if os.path.isfile(fpath):
                if now - os.path.getmtime(fpath) > ttl_seconds:
                    try:
                        os.remove(fpath)
                    except OSError:
                        pass
