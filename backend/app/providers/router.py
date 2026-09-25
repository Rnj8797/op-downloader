from typing import List
from app.providers.base import BaseMediaProvider, UnsupportedUrlException, DrmProtectedContentException
from app.providers.direct import DirectMediaProvider
from app.providers.open_archive import OpenArchiveProvider
from app.providers.public_api import PublicApiProvider
from app.providers.social import SocialMediaProvider

class ProviderRouter:
    """
    Central router that delegates URL inspection and download preparation
    to the appropriate authorized provider adapter.
    """
    def __init__(self):
        self.providers: List[BaseMediaProvider] = [
            DirectMediaProvider(),
            SocialMediaProvider(),
            OpenArchiveProvider(),
            PublicApiProvider()
        ]

    def resolve_provider(self, url: str) -> BaseMediaProvider:
        if not url or not url.strip():
            raise UnsupportedUrlException("URL cannot be empty.")

        trimmed = url.strip()

        # Global anti-circumvention filter
        lower = trimmed.lower()
        if any(marker in lower for marker in ("drm", "widevine", "playready", "fairplay", "license_key", ".mpd")):
            raise DrmProtectedContentException()

        for provider in self.providers:
            if provider.can_handle(trimmed):
                return provider

        raise UnsupportedUrlException()

provider_router = ProviderRouter()
