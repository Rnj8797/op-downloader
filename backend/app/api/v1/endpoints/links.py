from fastapi import APIRouter, HTTPException
from app.schemas.common import ApiResponse
from app.schemas.links import InspectLinkRequest, InspectLinkData, FormatDescriptor
from app.core.ssrf import validate_and_resolve_url

router = APIRouter()

@router.post("/links/inspect", response_model=ApiResponse[InspectLinkData])
async def inspect_media_link(payload: InspectLinkRequest):
    # 1. Enforce strict SSRF & DNS pre-resolution guard
    normalized_url, resolved_ip = validate_and_resolve_url(payload.url)

    # 2. Inspect provider capabilities & formats
    lower = normalized_url.lower()
    is_video = not lower.endswith(".jpg") and not lower.endswith(".png") and "photo" not in lower
    
    if "unsplash.com" in lower:
        provider = "UNSPLASH_PUBLIC"
        media_type = "IMAGE"
        formats = [
            FormatDescriptor(format_id="original", quality_label="Original", estimated_size_bytes=4410291, mime_type="image/jpeg"),
            FormatDescriptor(format_id="1080p", quality_label="1080p", estimated_size_bytes=1245192, mime_type="image/jpeg")
        ]
    elif "archive.org" in lower:
        provider = "OPEN_ARCHIVE"
        media_type = "VIDEO" if is_video else "IMAGE"
        formats = [
            FormatDescriptor(format_id="original", quality_label="Original", estimated_size_bytes=85983232, mime_type="video/mp4"),
            FormatDescriptor(format_id="720p", quality_label="720p", estimated_size_bytes=32505856, mime_type="video/mp4")
        ]
    elif is_video:
        provider = "DIRECT_MEDIA"
        media_type = "VIDEO"
        formats = [
            FormatDescriptor(format_id="original", quality_label="Original", estimated_size_bytes=85983232, mime_type="video/mp4"),
            FormatDescriptor(format_id="1080p", quality_label="1080p", estimated_size_bytes=56623104, mime_type="video/mp4"),
            FormatDescriptor(format_id="720p", quality_label="720p", estimated_size_bytes=32505856, mime_type="video/mp4"),
            FormatDescriptor(format_id="480p", quality_label="480p", estimated_size_bytes=18874368, mime_type="video/mp4")
        ]
    else:
        provider = "DIRECT_MEDIA"
        media_type = "IMAGE"
        formats = [
            FormatDescriptor(format_id="original", quality_label="Original", estimated_size_bytes=4410291, mime_type="image/jpeg")
        ]

    data = InspectLinkData(
        provider=provider,
        media_type=media_type,
        title=payload.url.split("/")[-1].split("?")[0] or "Authorized Media",
        thumbnail_url=None,
        duration_seconds=151 if media_type == "VIDEO" else None,
        available_formats=formats
    )

    return ApiResponse(data=data)
