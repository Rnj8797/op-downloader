from fastapi import APIRouter, HTTPException
from app.schemas.common import ApiResponse
from app.schemas.auth import RegisterRequest, LoginRequest, TokenData, RefreshTokenRequest
from app.core.security import hash_password, verify_password, create_access_token, verify_token, revoke_token
import uuid

router = APIRouter()

# Temporary in-memory user registry for authentication endpoints
USERS_STORE = {}

@router.post("/auth/register", response_model=ApiResponse[dict], status_code=201)
async def register(payload: RegisterRequest):
    email = payload.email.lower()
    if email in USERS_STORE:
        raise HTTPException(status_code=400, detail={"code": "EMAIL_EXISTS", "message": "An account with this email already exists."})

    user_id = str(uuid.uuid4())
    USERS_STORE[email] = {
        "id": user_id,
        "email": email,
        "password_hash": hash_password(payload.password),
        "role": "USER"
    }
    return ApiResponse(data={"user_id": user_id, "email": email})

@router.post("/auth/login", response_model=ApiResponse[TokenData])
async def login(payload: LoginRequest):
    email = payload.email.lower()
    user = USERS_STORE.get(email)
    if not user or not verify_password(payload.password, user["password_hash"]):
        raise HTTPException(status_code=401, detail={"code": "INVALID_CREDENTIALS", "message": "Invalid email or password."})

    access_token = create_access_token(subject=user["id"], role=user["role"], expires_minutes=15)
    refresh_token = create_access_token(subject=user["id"], role="REFRESH", expires_minutes=60 * 24 * 30)

    return ApiResponse(
        data=TokenData(
            access_token=access_token,
            refresh_token=refresh_token,
            token_type="bearer",
            expires_in=900
        )
    )

@router.post("/auth/refresh", response_model=ApiResponse[TokenData])
async def refresh_tokens(payload: RefreshTokenRequest):
    token_payload = verify_token(payload.refresh_token)
    if not token_payload or token_payload.get("role") != "REFRESH":
        raise HTTPException(status_code=401, detail={"code": "INVALID_TOKEN", "message": "Invalid or expired refresh token."})

    # Revoke old refresh token to enforce rotation
    revoke_token(payload.refresh_token)

    user_id = token_payload["sub"]
    new_access_token = create_access_token(subject=user_id, role="USER", expires_minutes=15)
    new_refresh_token = create_access_token(subject=user_id, role="REFRESH", expires_minutes=60 * 24 * 30)

    return ApiResponse(
        data=TokenData(
            access_token=new_access_token,
            refresh_token=new_refresh_token,
            token_type="bearer",
            expires_in=900
        )
    )

@router.post("/auth/logout", response_model=ApiResponse[dict])
async def logout(payload: Optional[RefreshTokenRequest] = None):
    if payload and payload.refresh_token:
        revoke_token(payload.refresh_token)
    return ApiResponse(data={"logged_out": True})
