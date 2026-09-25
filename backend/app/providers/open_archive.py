from app.providers.base import (
    BaseMediaProvider, MediaMetadata, MediaFormat, StreamDescriptor,
    UnsupportedUrlException, DrmProtectedContentException
)
from app.core.ssrf import validate_and_resolve_url
from urllib.parse import urlparse
import re

class OpenArchiveProvider(BaseMediaProvider):
    """
    Handles explicitly authorized open-access repositories like Internet Archive
    public domain collections and Wikimedia Commons.
    """
    @property
    def provider_id(self) -> str:
        return "OPEN_ARCHIVE"

    def can_handle(self, url: str) -> bool:
        lower = url.lower()
        return "archive.org/details/" in lower or "commons.wikimedia.org/wiki/File:" in lower

    async def validate_authorization(self, url: str) -> bool:
        validate_and_resolve_url(url)
        lower = url.lower()
        # Verify not a restricted-lending or borrow-only archive item
        if "borrow" in lower or "lending" in lower or "restricted" in lower:
            raise UnsupportedUrlException("This archive item has access restrictions and cannot be saved.")
        return True

    async def get_metadata(self, url: str) -> MediaMetadata:
        await self.validate_authorization(url)
        parsed = urlparse(url)
        identifier = parsed.path.split("/")[-1] or "Archive_Item"
        clean_title = re.sub(r"[^a-zA-Z0-9._-]", "_", identifier)

        is_video = "video" in url.lower() or "movie" in url.lower() or not ("jpg" in url.lower() or "png" in url.lower())

        formats = [
            MediaFormat(
                format_id="original",
                quality_label="Original",
                estimated_size_bytes=104857600 if is_video else 5242880,
                mime_type="video/mp4" if is_video else "image/jpeg",
                download_url=url
            ),
            MediaFormat(
                format_id="720p",
                quality_label="720p",
                estimated_size_bytes=41943040 if is_video else 2097152,
                mime_type="video/mp4" if is_video else "image/jpeg",
                download_url=url
            )
        ]

        return MediaMetadata(
            title=f"Archive_{clean_title}",
            provider=self.provider_id,
            media_type="VIDEO" if is_video else "IMAGE",
            duration_seconds=300 if is_video else None,
            available_formats=formats,
            license_type="PUBLIC_DOMAIN"
        )

    async def create_download_job(self, url: str, format_id: str) -> StreamDescriptor:
        await self.validate_authorization(url)
        metadata = await self.get_metadata(url)
        return StreamDescriptor(
            target_url=url,
            mime_type="video/mp4" if metadata.media_type == "VIDEO" else "image/jpeg",
            total_bytes=metadata.available_formats[0].estimated_size_bytes,
            supports_range_requests=True,
            safe_filename=f"{metadata.title}.{'mp4' if metadata.media_type == 'VIDEO' else 'jpg'}"
        )
