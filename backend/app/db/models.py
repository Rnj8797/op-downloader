from sqlalchemy import (
    Column, String, Boolean, DateTime, BigInteger, Integer, ForeignKey, JSON
)
from sqlalchemy.dialects.postgresql import UUID as PG_UUID
from sqlalchemy.orm import relationship
import uuid
import datetime
from app.db.base import Base

def generate_uuid_str():
    return str(uuid.uuid4())

class User(Base):
    __tablename__ = "users"

    id = Column(String(36), primary_key=True, default=generate_uuid_str)
    email = Column(String(255), unique=True, nullable=True, index=True)
    password_hash = Column(String(255), nullable=True)
    role = Column(String(32), default="USER", nullable=False)
    is_active = Column(Boolean, default=True, nullable=False)
    created_at = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    updated_at = Column(DateTime, default=datetime.datetime.utcnow, onupdate=datetime.datetime.utcnow, nullable=False)

    devices = relationship("Device", back_populates="user", cascade="all, delete-orphan")
    jobs = relationship("DownloadJob", back_populates="user")
    sessions = relationship("Session", back_populates="user", cascade="all, delete-orphan")


class Device(Base):
    __tablename__ = "devices"

    id = Column(String(36), primary_key=True, default=generate_uuid_str)
    user_id = Column(String(36), ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    device_fingerprint_hash = Column(String(64), unique=True, nullable=False, index=True)
    client_version = Column(String(32), nullable=False)
    os_version = Column(String(32), nullable=False)
    last_seen_at = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    created_at = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)

    user = relationship("User", back_populates="devices")
    jobs = relationship("DownloadJob", back_populates="device")


class Session(Base):
    __tablename__ = "sessions"

    id = Column(String(36), primary_key=True, default=generate_uuid_str)
    user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    device_id = Column(String(36), ForeignKey("devices.id", ondelete="SET NULL"), nullable=True)
    refresh_token_hash = Column(String(64), unique=True, nullable=False, index=True)
    expires_at = Column(DateTime, nullable=False)
    is_revoked = Column(Boolean, default=False, nullable=False)
    client_ip_hash = Column(String(64), nullable=False)
    created_at = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)

    user = relationship("User", back_populates="sessions")


class DownloadJob(Base):
    __tablename__ = "download_jobs"

    id = Column(String(36), primary_key=True, default=generate_uuid_str)
    user_id = Column(String(36), ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    device_id = Column(String(36), ForeignKey("devices.id", ondelete="CASCADE"), nullable=False, index=True)
    source_url_hash = Column(String(64), nullable=False, index=True)
    provider = Column(String(64), nullable=False)
    media_type = Column(String(32), nullable=False)
    status = Column(String(32), default="QUEUED", nullable=False, index=True)
    file_size_bytes = Column(BigInteger, nullable=True)
    quality_selected = Column(String(32), default="ORIGINAL", nullable=False)
    error_code = Column(String(64), nullable=True)
    created_at = Column(DateTime, default=datetime.datetime.utcnow, nullable=False, index=True)
    started_at = Column(DateTime, nullable=True)
    completed_at = Column(DateTime, nullable=True)

    user = relationship("User", back_populates="jobs")
    device = relationship("Device", back_populates="jobs")
    items = relationship("DownloadItem", back_populates="job", cascade="all, delete-orphan")
    metadata_record = relationship("ProviderMetadata", back_populates="job", uselist=False, cascade="all, delete-orphan")


class DownloadItem(Base):
    __tablename__ = "download_items"

    id = Column(String(36), primary_key=True, default=generate_uuid_str)
    job_id = Column(String(36), ForeignKey("download_jobs.id", ondelete="CASCADE"), nullable=False, index=True)
    safe_filename = Column(String(255), nullable=False)
    mime_type = Column(String(128), nullable=False)
    checksum_sha256 = Column(String(64), nullable=True)
    bytes_downloaded = Column(BigInteger, default=0, nullable=False)
    total_bytes = Column(BigInteger, default=0, nullable=False)
    storage_path = Column(String(512), nullable=True)
    expires_at = Column(DateTime, nullable=False, index=True)

    job = relationship("DownloadJob", back_populates="items")


class ProviderMetadata(Base):
    __tablename__ = "provider_metadata"

    id = Column(String(36), primary_key=True, default=generate_uuid_str)
    job_id = Column(String(36), ForeignKey("download_jobs.id", ondelete="CASCADE"), nullable=False, unique=True)
    provider_id = Column(String(64), nullable=False)
    content_author_display = Column(String(255), nullable=True)
    content_title_sanitized = Column(String(255), nullable=True)
    duration_seconds = Column(Integer, nullable=True)
    available_formats = Column(JSON, nullable=True)
    fetched_at = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)

    job = relationship("DownloadJob", back_populates="metadata_record")


class RateLimitRecord(Base):
    __tablename__ = "rate_limit_records"

    id = Column(String(36), primary_key=True, default=generate_uuid_str)
    bucket_key = Column(String(128), unique=True, nullable=False, index=True)
    request_count = Column(Integer, default=1, nullable=False)
    window_expires_at = Column(DateTime, nullable=False, index=True)


class AuditLog(Base):
    __tablename__ = "audit_logs"

    id = Column(String(36), primary_key=True, default=generate_uuid_str)
    user_id = Column(String(36), ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    event_type = Column(String(64), nullable=False, index=True)
    ip_address_masked = Column(String(64), nullable=False)
    user_agent_summary = Column(String(255), nullable=True)
    details = Column(JSON, nullable=True)
    created_at = Column(DateTime, default=datetime.datetime.utcnow, nullable=False, index=True)
