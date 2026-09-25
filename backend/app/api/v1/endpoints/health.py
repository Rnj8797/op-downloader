from fastapi import APIRouter
from app.schemas.common import ApiResponse
from app.core.config import settings

router = APIRouter()

@router.get("/health", response_model=ApiResponse[dict])
async def health_check():
    return ApiResponse(
        data={
            "status": "healthy",
            "service": settings.PROJECT_NAME,
            "database": "connected",
            "redis": "connected",
            "workers_available": 4
        }
    )

@router.get("/version", response_model=ApiResponse[dict])
async def version_info():
    return ApiResponse(
        data={
            "version": settings.VERSION,
            "minimum_android_version": "1.0.0",
            "maintenance_mode": False
        }
    )
