from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.exceptions import RequestValidationError
from app.core.config import settings
from app.core.ssrf import SSRFProtectionError
from app.core.exceptions import (
    ssrf_exception_handler,
    validation_exception_handler,
    http_exception_handler,
    global_exception_handler
)
from app.api.v1.router import api_router

app = FastAPI(
    title=settings.PROJECT_NAME,
    version=settings.VERSION,
    openapi_url=None if settings.APP_ENV == "production" else "/api/v1/openapi.json",
    docs_url=None if settings.APP_ENV == "production" else "/api/v1/docs",
    redoc_url=None if settings.APP_ENV == "production" else "/api/v1/redoc"
)

# Enforce secure CORS (never allow wildcard with credentials)
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.ALLOWED_CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["GET", "POST", "DELETE", "OPTIONS"],
    allow_headers=["*"],
)

# Register custom sanitized exception handlers
app.add_exception_handler(SSRFProtectionError, ssrf_exception_handler)
app.add_exception_handler(RequestValidationError, validation_exception_handler)
app.add_exception_handler(HTTPException, http_exception_handler)
app.add_exception_handler(Exception, global_exception_handler)

# Include API v1 router
app.include_router(api_router, prefix=settings.API_V1_STR)

@app.get("/")
async def root():
    return {
        "service": settings.PROJECT_NAME,
        "status": "online",
        "docs": "/api/v1/docs"
    }
