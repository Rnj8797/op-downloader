from fastapi import APIRouter, HTTPException, Header, Query, Depends
from app.schemas.common import ApiResponse
from app.schemas.downloads import (
    CreateDownloadRequest,
    DownloadJobData,
    DownloadProgressData,
    DownloadListData,
    DownloadHistoryItem
)
from app.core.ssrf import validate_and_resolve_url
from app.core.security import verify_token
from app.db.redis import redis_service
from typing import Optional
import uuid
import datetime

router = APIRouter()

# Multi-tenant in-memory job store keyed by job_id
JOBS_STORE = {
    "sample-video-1": {
        "job_id": "sample-video-1",
        "owner_id": "default_device",
        "safe_filename": "BigBuckBunny.mp4",
        "media_type": "VIDEO",
        "status": "COMPLETED",
        "bytes_downloaded": 158008374,
        "total_bytes": 158008374,
        "completed_at": "2026-09-25T14:20:00Z"
    }
}

def get_caller_identity(
    authorization: Optional[str] = Header(None),
    x_device_id: Optional[str] = Header(None)
) -> tuple[str, str]:
    """
    Extracts (caller_id, role) from JWT Bearer token or X-Device-Id header.
    """
    if authorization and authorization.startswith("Bearer "):
        token = authorization.split(" ")[1]
        payload = verify_token(token)
        if payload:
            return payload["sub"], payload.get("role", "USER")

    if x_device_id and len(x_device_id.strip()) >= 8:
        return x_device_id.strip(), "ANONYMOUS"

    return "anonymous_default", "ANONYMOUS"

@router.post("/downloads", response_model=ApiResponse[DownloadJobData], status_code=202)
async def create_download_job(
    payload: CreateDownloadRequest,
    caller: tuple[str, str] = Depends(get_caller_identity)
):
    caller_id, role = caller

    # 1. Rate limiting check
    hourly_limit = 50 if role == "USER" else 10
    if not await redis_service.check_rate_limit(f"user:{caller_id}", max_requests=hourly_limit):
        raise HTTPException(
            status_code=429,
            detail={"code": "RATE_LIMIT_EXCEEDED", "message": "Rate limit exceeded. Please wait."}
        )

    # 2. Concurrency limit check
    if not await redis_service.acquire_concurrency_slot(caller_id, max_concurrent=2):
        raise HTTPException(
            status_code=429,
            detail={"code": "CONCURRENCY_LIMIT_EXCEEDED", "message": "Maximum concurrent downloads reached."}
        )

    # 3. SSRF guard on the target URL
    validate_and_resolve_url(payload.url)

    job_id = str(uuid.uuid4())
    is_video = not payload.url.lower().endswith(".jpg") and not payload.url.lower().endswith(".png")
    
    job_record = {
        "job_id": job_id,
        "owner_id": caller_id,
        "safe_filename": f"{payload.safe_title}.{'mp4' if is_video else 'jpg'}",
        "media_type": "VIDEO" if is_video else "IMAGE",
        "status": "QUEUED",
        "bytes_downloaded": 0,
        "total_bytes": 85983232 if is_video else 4410291,
        "created_at": datetime.datetime.utcnow().isoformat() + "Z"
    }
    JOBS_STORE[job_id] = job_record

    data = DownloadJobData(
        job_id=job_id,
        status="QUEUED",
        provider="DIRECT_MEDIA",
        media_type="VIDEO" if is_video else "IMAGE",
        estimated_size_bytes=job_record["total_bytes"],
        created_at=job_record["created_at"]
    )
    return ApiResponse(data=data)

@router.get("/downloads", response_model=ApiResponse[DownloadListData])
async def list_downloads(caller: tuple[str, str] = Depends(get_caller_identity)):
    caller_id, role = caller

    # Multi-tenant isolation: Only return caller's own jobs (or all if ADMIN)
    items = [
        DownloadHistoryItem(
            job_id=j["job_id"],
            safe_filename=j["safe_filename"],
            media_type=j["media_type"],
            status=j["status"],
            bytes_downloaded=j["bytes_downloaded"],
            total_bytes=j["total_bytes"],
            completed_at=j.get("completed_at")
        )
        for j in JOBS_STORE.values()
        if role == "ADMIN" or j.get("owner_id") == caller_id
    ]
    return ApiResponse(data=DownloadListData(items=items, total=len(items)))

@router.get("/downloads/{job_id}", response_model=ApiResponse[DownloadProgressData])
async def get_download_progress(job_id: str, caller: tuple[str, str] = Depends(get_caller_identity)):
    caller_id, role = caller
    job = JOBS_STORE.get(job_id)

    # Multi-tenant access control: Hide existence from unauthorized users (404)
    if not job or (role != "ADMIN" and job.get("owner_id") != caller_id):
        raise HTTPException(
            status_code=404,
            detail={"code": "RESOURCE_NOT_FOUND", "message": "The download job was not found."}
        )

    total = job["total_bytes"]
    downloaded = job["bytes_downloaded"]
    pct = round((downloaded / total * 100), 1) if total > 0 else 0.0

    data = DownloadProgressData(
        job_id=job_id,
        status=job["status"],
        progress_percentage=pct,
        bytes_downloaded=downloaded,
        total_bytes=total,
        speed_bytes_per_sec=8808038 if job["status"] == "DOWNLOADING" else 0,
        eta_seconds=12 if job["status"] == "DOWNLOADING" else 0,
        temporary_download_url=None
    )
    return ApiResponse(data=data)

@router.post("/downloads/{job_id}/pause", response_model=ApiResponse[dict])
async def pause_download(job_id: str, caller: tuple[str, str] = Depends(get_caller_identity)):
    caller_id, role = caller
    job = JOBS_STORE.get(job_id)
    if not job or (role != "ADMIN" and job.get("owner_id") != caller_id):
        raise HTTPException(status_code=404, detail={"code": "RESOURCE_NOT_FOUND", "message": "The download job was not found."})

    job["status"] = "PAUSED"
    return ApiResponse(data={"status": "PAUSED"})

@router.post("/downloads/{job_id}/resume", response_model=ApiResponse[dict])
async def resume_download(job_id: str, caller: tuple[str, str] = Depends(get_caller_identity)):
    caller_id, role = caller
    job = JOBS_STORE.get(job_id)
    if not job or (role != "ADMIN" and job.get("owner_id") != caller_id):
        raise HTTPException(status_code=404, detail={"code": "RESOURCE_NOT_FOUND", "message": "The download job was not found."})

    job["status"] = "QUEUED"
    return ApiResponse(data={"status": "QUEUED"})

@router.post("/downloads/{job_id}/cancel", response_model=ApiResponse[dict])
async def cancel_download(job_id: str, caller: tuple[str, str] = Depends(get_caller_identity)):
    caller_id, role = caller
    job = JOBS_STORE.get(job_id)
    if not job or (role != "ADMIN" and job.get("owner_id") != caller_id):
        raise HTTPException(status_code=404, detail={"code": "RESOURCE_NOT_FOUND", "message": "The download job was not found."})

    job["status"] = "CANCELLED"
    await redis_service.release_concurrency_slot(caller_id)
    return ApiResponse(data={"status": "CANCELLED"})

@router.delete("/downloads/{job_id}", response_model=ApiResponse[dict])
async def delete_download(job_id: str, caller: tuple[str, str] = Depends(get_caller_identity)):
    caller_id, role = caller
    job = JOBS_STORE.get(job_id)
    if not job or (role != "ADMIN" and job.get("owner_id") != caller_id):
        raise HTTPException(status_code=404, detail={"code": "RESOURCE_NOT_FOUND", "message": "The download job was not found."})

    del JOBS_STORE[job_id]
    await redis_service.release_concurrency_slot(caller_id)
    return ApiResponse(data={"deleted": True})
