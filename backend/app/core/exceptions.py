from fastapi import Request, HTTPException
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from app.core.ssrf import SSRFProtectionError
import logging

logger = logging.getLogger("op_downloader")

async def ssrf_exception_handler(request: Request, exc: SSRFProtectionError):
    logger.warning(f"SSRF or URL validation block: {exc.code} - {exc.message}")
    return JSONResponse(
        status_code=400,
        content={
            "success": False,
            "error": {
                "code": exc.code,
                "message": "This link isn't supported."
            }
        }
    )

async def validation_exception_handler(request: Request, exc: RequestValidationError):
    return JSONResponse(
        status_code=422,
        content={
            "success": False,
            "error": {
                "code": "VALIDATION_ERROR",
                "message": "The request payload contains invalid fields."
            }
        }
    )

async def http_exception_handler(request: Request, exc: HTTPException):
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "success": False,
            "error": {
                "code": getattr(exc, "detail", {}).get("code", "REQUEST_ERROR") if isinstance(exc.detail, dict) else "REQUEST_ERROR",
                "message": exc.detail.get("message", str(exc.detail)) if isinstance(exc.detail, dict) else str(exc.detail)
            }
        }
    )

async def global_exception_handler(request: Request, exc: Exception):
    logger.error(f"Unhandled exception: {exc}", exc_info=True)
    # Zero stack trace or internal error strings returned to client
    return JSONResponse(
        status_code=500,
        content={
            "success": False,
            "error": {
                "code": "SERVER_ERROR",
                "message": "Something went wrong. Please try again."
            }
        }
    )
