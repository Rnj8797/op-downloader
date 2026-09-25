from pydantic import BaseModel, Field
from typing import List, Optional

class InspectLinkRequest(BaseModel):
    url: str = Field(..., max_length=2048, description="Target media URL to inspect")

class FormatDescriptor(BaseModel):
    format_id: str
    quality_label: str
    estimated_size_bytes: Optional[int] = None
    mime_type: str

class InspectLinkData(BaseModel):
    provider: str
    media_type: str  # "VIDEO" | "IMAGE" | "AUDIO"
    title: str
    thumbnail_url: Optional[str] = None
    duration_seconds: Optional[int] = None
    available_formats: List[FormatDescriptor]
