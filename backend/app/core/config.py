from typing import List
import os

try:
    from pydantic_settings import BaseSettings
    class ConfigBase(BaseSettings):
        class Config:
            case_sensitive = True
            env_file = ".env"
            extra = "allow"
except ImportError:
    try:
        from pydantic import BaseSettings
        class ConfigBase(BaseSettings):
            pass
    except ImportError:
        class ConfigBase:
            def __init__(self, **kwargs):
                for k, v in kwargs.items():
                    setattr(self, k, v)

class Settings(ConfigBase):
    PROJECT_NAME: str = "OP Downloader API"
    VERSION: str = "1.0.0"
    API_V1_STR: str = "/api/v1"
    APP_ENV: str = os.getenv("APP_ENV", "development")
    
    # Security & JWT
    JWT_SECRET: str = os.getenv("JWT_SECRET", "op_downloader_secure_jwt_secret_change_in_production_32b_min")
    JWT_ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 15
    REFRESH_TOKEN_EXPIRE_DAYS: int = 30
    
    # Rate Limiting Defaults
    RATE_LIMIT_ANONYMOUS_HOURLY: int = 10
    RATE_LIMIT_AUTHENTICATED_HOURLY: int = 50
    MAX_CONCURRENT_DOWNLOADS_PER_USER: int = 2
    
    # Storage & File Limits
    MAX_DOWNLOAD_SIZE_BYTES: int = 2 * 1024 * 1024 * 1024  # 2 GB
    SCRATCH_STORAGE_DIR: str = os.getenv("SCRATCH_STORAGE_DIR", "/var/media_scratch")
    SCRATCH_TTL_MINUTES: int = 60
    
    # Network Security & CORS
    ALLOWED_CORS_ORIGINS: List[str] = [
        "https://opdownloader.app",
        "http://localhost:3000"
    ]

settings = Settings()
