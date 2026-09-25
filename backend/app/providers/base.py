from abc import ABC, abstractmethod
from typing import List, Optional
from pydantic import BaseModel

class MediaFormat(BaseModel):
    format_id: str
    quality_label: str
    estimated_size_bytes: Optional[int] = None
    mime_type: str
    download_url: str

class MediaMetadata(BaseModel):
    title: str
    provider: str
    media_type: str  # "VIDEO" | "IMAGE" | "AUDIO"
    thumbnail_url: Optional[str] = None
    duration_seconds: Optional[int] = None
    available_formats: List[MediaFormat]
    author: Optional[str] = None
    license_type: Optional[str] = "PUBLIC_OR_USER_OWNED"

class StreamDescriptor(BaseModel):
    target_url: str
    mime_type: str
    total_bytes: Optional[int] = None
    supports_range_requests: bool = True
    safe_filename: str

class UnsupportedUrlException(Exception):
    def __init__(self, message: str = "This link isn't supported.", code: str = "UNSUPPORTED_URL"):
        self.message = message
        self.code = code
        super().__init__(self.message)

class DrmProtectedContentException(Exception):
    def __init__(self, message: str = "DRM-protected content cannot be downloaded.", code: str = "DRM_RESTRICTED"):
        self.message = message
        self.code = code
        super().__init__(self.message)

class BaseMediaProvider(ABC):
    """
    Abstract interface for all media provider adapters.
    Each provider must be independently testable and replaceable.
    """
    @property
    @abstractmethod
    def provider_id(self) -> str:
        pass

    @abstractmethod
    def can_handle(self, url: str) -> bool:
        """Determines if this provider recognizes and supports the URL structure."""
        pass

    @abstractmethod
    async def get_metadata(self, url: str) -> MediaMetadata:
        """Extracts media title, thumbnail, duration, and available authorized qualities."""
        pass

    @abstractmethod
    async def validate_authorization(self, url: str) -> bool:
        """
        Verifies that content is public or user-authorized.
        Rejects DRM, paywalled, or login-restricted media.
        """
        pass

    @abstractmethod
    async def create_download_job(self, url: str, format_id: str) -> StreamDescriptor:
        """Prepares the stream descriptor and headers for chunked/resumable worker download."""
        pass
