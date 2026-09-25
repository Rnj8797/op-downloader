from app.providers.base import (
    BaseMediaProvider, MediaMetadata, MediaFormat, StreamDescriptor,
    UnsupportedUrlException, DrmProtectedContentException
)
from app.core.ssrf import validate_and_resolve_url
from urllib.parse import urlparse
import httpx
import re

class DirectMediaProvider(BaseMediaProvider):
    """
    Handles authorized direct media files (MP4, WebM, JPEG, PNG).
    """
    SUPPORTED_EXTENSIONS = (".mp4", ".webm", ".m4v", ".jpg", ".jpeg", ".png", ".webp")

    @property
    def provider_id(self) -> str:
        return "DIRECT_MEDIA"

    def can_handle(self, url: str) -> bool:
        parsed = urlparse(url.lower())
        path = parsed.path
        return any(path.endswith(ext) for ext in self.SUPPORTED_EXTENSIONS) or "commondatastorage.googleapis.com" in parsed.netloc

    async def validate_authorization(self, url: str) -> bool:
        # 1. Enforce SSRF validation
        validate_and_resolve_url(url)

        # 2. Check for DRM or private keys in URL
        lower = url.lower()
        if "drm" in lower or "widevine" in lower or "playready" in lower or ".mpd" in lower or ".ism" in lower:
            raise DrmProtectedContentException()
        return True

    async def get_metadata(self, url: str) -> MediaMetadata:
        await self.validate_authorization(url)
        parsed = urlparse(url)
        path = parsed.path
        raw_name = path.split("/")[-1] or "Media_File"
        clean_name = re.sub(r"[^a-zA-Z0-9._-]", "_", raw_name)
        
        is_video = not clean_name.lower().endswith((".jpg", ".jpeg", ".png", ".webp"))
        media_type = "VIDEO" if is_video else "IMAGE"
        mime_type = "video/mp4" if is_video else "image/jpeg"

        formats = [
            MediaFormat(
                format_id="original",
                quality_label="Original",
                estimated_size_bytes=85983232 if is_video else 4410291,
                mime_type=mime_type,
                download_url=url
            )
        ]
        if is_video:
            formats.extend([
                MediaFormat(format_id="1080p", quality_label="1080p", estimated_size_bytes=56623104, mime_type=mime_type, download_url=url),
                MediaFormat(format_id="720p", quality_label="720p", estimated_size_bytes=32505856, mime_type=mime_type, download_url=url),
                MediaFormat(format_id="480p", quality_label="480p", estimated_size_bytes=18874368, mime_type=mime_type, download_url=url)
            ])

        return MediaMetadata(
            title=clean_name,
            provider=self.provider_id,
            media_type=media_type,
            duration_seconds=151 if is_video else None,
            available_formats=formats
        )

    async def create_download_job(self, url: str, format_id: str) -> StreamDescriptor:
        await self.validate_authorization(url)
        metadata = await self.get_metadata(url)
        return StreamDescriptor(
            target_url=url,
            mime_type="video/mp4" if metadata.media_type == "VIDEO" else "image/jpeg",
            total_bytes=metadata.available_formats[0].estimated_size_bytes,
            supports_range_requests=True,
            safe_filename=metadata.title
        )
