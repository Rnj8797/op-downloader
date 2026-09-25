from fastapi import APIRouter
from app.api.v1.endpoints import health, links, downloads, auth

api_router = APIRouter()

api_router.include_router(health.router, tags=["Health"])
api_router.include_router(links.router, tags=["Links"])
api_router.include_router(downloads.router, tags=["Downloads"])
api_router.include_router(auth.router, tags=["Auth"])
