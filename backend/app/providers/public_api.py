from app.providers.base import (
    BaseMediaProvider, MediaMetadata, MediaFormat, StreamDescriptor,
    UnsupportedUrlException
)
from app.core.ssrf import validate_and_resolve_url
from urllib.parse import urlparse
import re

class PublicApiProvider(BaseMediaProvider):
    """
    Handles authorized public API providers (e.g., Unsplash open license photos)
    using permitted public endpoints without scraping or authentication bypass.
    """
    @property
    def provider_id(self) -> str:
        return "PUBLIC_API_MEDIA"

    def can_handle(self, url: str) -> bool:
        lower = url.lower()
        return "unsplash.com/photos/" in lower or "images.unsplash.com/" in lower

    async def validate_authorization(self, url: str) -> bool:
        validate_and_resolve_url(url)
        return True

    async def get_metadata(self, url: str) -> MediaMetadata:
        await self.validate_authorization(url)
        parsed = urlparse(url)
        photo_id = parsed.path.split("/")[-1] or "Photo"
        clean_id = re.sub(r"[^a-zA-Z0-9_-]", "_", photo_id)

        formats = [
            MediaFormat(
                format_id="original",
                quality_label="Original (Raw)",
                estimated_size_bytes=5242880,
                mime_type="image/jpeg",
                download_url=f"https://images.unsplash.com/{clean_id}?auto=format&fit=crop&w=2400&q=100"
            ),
            MediaFormat(
                format_id="1080p",
                quality_label="1080p (Full HD)",
                estimated_size_bytes=1572864,
                mime_type="image/jpeg",
                download_url=f"https://images.unsplash.com/{clean_id}?auto=format&fit=crop&w=1080&q=80"
            )
        ]

        return MediaMetadata(
            title=f"Unsplash_{clean_id}",
            provider="UNSPLASH",
            media_type="IMAGE",
            thumbnail_url=f"https://images.unsplash.com/{clean_id}?auto=format&fit=crop&w=200&q=60",
            duration_seconds=None,
            available_formats=formats,
            license_type="UNSPLASH_OPEN_LICENSE"
        )

    async def create_download_job(self, url: str, format_id: str) -> StreamDescriptor:
        await self.validate_authorization(url)
        metadata = await self.get_metadata(url)
        target = next((f for f in metadata.available_formats if f.format_id == format_id), metadata.available_formats[0])
        return StreamDescriptor(
            target_url=target.download_url,
            mime_type=target.mime_type,
            total_bytes=target.estimated_size_bytes,
            supports_range_requests=True,
            safe_filename=f"{metadata.title}.jpg"
        )
