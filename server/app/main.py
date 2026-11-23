import hmac
import json
import logging
from typing import List, Optional
from urllib.parse import unquote

from fastapi import Depends, FastAPI, File, Header, HTTPException, Request, UploadFile
from fastapi.responses import FileResponse, JSONResponse, Response
from starlette.concurrency import run_in_threadpool

from . import config
from .storage import NotPrimary, RevisionConflict, Storage

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("cola-sync")

app = FastAPI(
    title="Cola Sync",
    docs_url=None,
    redoc_url=None,
    openapi_url=None,
)

_storage: Optional[Storage] = None


def storage() -> Storage:
    global _storage
    if _storage is None:
        _storage = Storage()
    return _storage


@app.on_event("startup")
def startup() -> None:
    if not config.TOKEN:
        raise RuntimeError("COLA_SYNC_TOKEN is not configured")
    storage()
    log.info("cola-sync started, root=%s", config.ROOT)


def require_token(authorization: Optional[str] = Header(default=None)) -> None:
    if not config.TOKEN:
        raise HTTPException(status_code=500, detail="服务器未配置 Token")
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="缺少认证 Token")
    token = authorization[len("Bearer "):].strip()
    if not hmac.compare_digest(token, config.TOKEN):
        raise HTTPException(status_code=401, detail="Token 不正确")


@app.get("/api/v1/health")
def health():
    return {"status": "ok", "version": 1}


@app.get("/api/v1/state")
def state(_: None = Depends(require_token)):
    return storage().state()


@app.post("/api/v1/images")
async def upload_images(
    files: List[UploadFile] = File(...),
    x_device_id: Optional[str] = Header(default=None),
    _: None = Depends(require_token),
):
    if len(files) > config.MAX_FILES_PER_REQUEST:
        raise HTTPException(status_code=400, detail=f"一次最多上传 {config.MAX_FILES_PER_REQUEST} 张照片")
    try:
        return await storage().save_images(files, device_id=unquote(x_device_id or ""))
    except NotPrimary as e:
        return JSONResponse(
            status_code=403,
            content={
                "error": "not_primary",
                "primaryDeviceId": e.primary_device_id,
                "primaryDeviceName": e.primary_device_name,
                "detail": "本机不是主设备，不能上传",
            },
        )
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        log.exception("upload images failed")
        raise HTTPException(status_code=500, detail=f"保存照片失败：{e}")


@app.post("/api/v1/data")
async def upload_data(
    request: Request,
    x_device_id: Optional[str] = Header(default=None),
    x_device_name: Optional[str] = Header(default=None),
    x_base_revision: Optional[str] = Header(default=None),
    _: None = Depends(require_token),
):
    body = await request.body()
    if len(body) == 0:
        raise HTTPException(status_code=400, detail="请求体为空")
    if len(body) > config.MAX_DATA_BYTES:
        raise HTTPException(status_code=413, detail="数据文件过大")

    base_revision: Optional[int] = None
    if x_base_revision is not None and x_base_revision.strip() != "":
        try:
            base_revision = int(x_base_revision.strip())
        except ValueError:
            raise HTTPException(status_code=400, detail="X-Base-Revision 不是整数")

    try:
        result = await storage().save_data(
            raw=body,
            device_id=unquote(x_device_id or "")[:120],
            device_name=unquote(x_device_name or "")[:120],
            base_revision=base_revision,
        )
    except RevisionConflict as e:
        return JSONResponse(
            status_code=409,
            content={"error": "revision_conflict", "server": e.state},
        )
    except NotPrimary as e:
        return JSONResponse(
            status_code=403,
            content={
                "error": "not_primary",
                "primaryDeviceId": e.primary_device_id,
                "primaryDeviceName": e.primary_device_name,
                "detail": "本机不是主设备，不能上传",
            },
        )
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        log.exception("upload data failed")
        raise HTTPException(status_code=500, detail=f"保存数据失败：{e}")

    return result


@app.get("/api/v1/backup.zip")
async def backup_zip(
    if_none_match: Optional[str] = Header(default=None),
    _: None = Depends(require_token),
):
    info = await run_in_threadpool(storage().build_backup_zip)
    if info is None:
        raise HTTPException(status_code=404, detail="云端还没有备份数据")

    etag = f'"{info["sha256"]}"'
    if if_none_match and if_none_match.strip() == etag:
        return Response(status_code=304, headers={"ETag": etag})

    return FileResponse(
        path=str(info["path"]),
        media_type="application/zip",
        filename=f"cola-backup-r{info['revision']}.zip",
        headers={
            "ETag": etag,
            "X-Snapshot-Revision": str(info["revision"]),
            "X-Images-Missing": str(info["imagesMissing"]),
        },
    )
