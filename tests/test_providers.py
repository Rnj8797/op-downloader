import asyncio
import sys
import os

backend_path = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "backend"))
sys.path.insert(0, backend_path)

from app.providers.router import provider_router
from app.providers.base import UnsupportedUrlException, DrmProtectedContentException
from app.providers.direct import DirectMediaProvider
from app.providers.open_archive import OpenArchiveProvider
from app.providers.public_api import PublicApiProvider

async def test_provider_resolution():
    # 1. Direct Media Resolution
    direct_url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
    provider = provider_router.resolve_provider(direct_url)
    assert isinstance(provider, DirectMediaProvider)
    metadata = await provider.get_metadata(direct_url)
    assert metadata.title == "BigBuckBunny.mp4"
    assert metadata.media_type == "VIDEO"
    assert len(metadata.available_formats) >= 3
    print("✓ DirectMediaProvider resolution and metadata extraction passed.")

    # 2. Open Archive Resolution
    archive_url = "https://archive.org/details/Electra_Video_Sample"
    archive_provider = provider_router.resolve_provider(archive_url)
    assert isinstance(archive_provider, OpenArchiveProvider)
    archive_meta = await archive_provider.get_metadata(archive_url)
    assert archive_meta.license_type == "PUBLIC_DOMAIN"
    print("✓ OpenArchiveProvider resolution passed.")

    # 3. Public API Provider Resolution
    unsplash_url = "https://images.unsplash.com/photo-1579783902614-a3fb3927b675"
    unsplash_provider = provider_router.resolve_provider(unsplash_url)
    assert isinstance(unsplash_provider, PublicApiProvider)
    unsplash_meta = await unsplash_provider.get_metadata(unsplash_url)
    assert unsplash_meta.media_type == "IMAGE"
    print("✓ PublicApiProvider resolution passed.")

    # 4. Rejection of Unsupported URLs
    try:
        provider_router.resolve_provider("https://unsupported-unknown-site.org/video")
        assert False, "Should have rejected unsupported domain"
    except UnsupportedUrlException:
        print("✓ Unsupported URL correctly rejected with UnsupportedUrlException.")

    # 5. Anti-Circumvention / DRM Rejection
    try:
        provider_router.resolve_provider("https://example.com/stream.mpd?drm=widevine")
        assert False, "Should have rejected DRM content"
    except DrmProtectedContentException:
        print("✓ DRM protected stream correctly rejected with DrmProtectedContentException.")

if __name__ == "__main__":
    asyncio.run(test_provider_resolution())
    print("\nALL AUTHORIZED PROVIDER FRAMEWORK TESTS PASSED SUCCESSFULLY!")
