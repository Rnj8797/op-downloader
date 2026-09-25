from pydantic import BaseModel, Field
from typing import List, Optional
from datetime import datetime

class CreateDownloadRequest(BaseModel):
    url: str = Field(..., max_length=2048)
    format_id: str = Field("original", max_length=32)
    safe_title: str = Field(..., max_length=128)

class DownloadJobData(BaseModel):
    job_id: str
    status: str
    provider: str
    media_type: str
    estimated_size_bytes: Optional[int] = None
    created_at: str

class DownloadProgressData(BaseModel):
    job_id: str
    status: str
    progress_percentage: float
    bytes_downloaded: int
    total_bytes: int
    speed_bytes_per_sec: int
    eta_seconds: int
    temporary_download_url: Optional[str] = None

class DownloadHistoryItem(BaseModel):
    job_id: str
    safe_filename: str
    media_type: str
    status: str
    bytes_downloaded: int
    total_bytes: int
    completed_at: Optional[str] = None

class DownloadListData(BaseModel):
    items: List[DownloadHistoryItem]
    total: int
