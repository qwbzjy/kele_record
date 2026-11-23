import asyncio
import hashlib
import json
import os
import re
import time
import zipfile
from pathlib import Path
from typing import Iterable

from . import config

IMAGE_RE = re.compile(r"^[A-Za-z0-9_-]{1,80}\.(jpg|jpeg|png|webp)$")
JPEG_MAGIC = b"\xff\xd8\xff"


class RevisionConflict(Exception):
    def __init__(self, state: dict):
        self.state = state
        super().__init__("revision conflict")


class NotPrimary(Exception):
    def __init__(self, primary_device_id: str, primary_device_name: str):
        self.primary_device_id = primary_device_id
        self.primary_device_name = primary_device_name
        super().__init__("not primary device")


def now_ms() -> int:
    return int(time.time() * 1000)


def _atomic_write(path: Path, data: bytes) -> None:
    tmp = config.ROOT / "tmp" / f"{path.name}.{os.getpid()}.{time.time_ns()}.tmp"
    tmp.parent.mkdir(parents=True, exist_ok=True)
    with open(tmp, "wb") as f:
        f.write(data)
        f.flush()
        os.fsync(f.fileno())
    os.replace(tmp, path)


def _read_json(path: Path, default=None):
    try:
        with open(path, "rb") as f:
            return json.loads(f.read().decode("utf-8"))
    except FileNotFoundError:
        return default
    except Exception:
        return default


def collect_photo_names(obj) -> set[str]:
    names: set[str] = set()

    def walk(value):
        if isinstance(value, dict):
            for key, item in value.items():
                if key == "photos" and isinstance(item, str):
                    for part in item.split(","):
                        name = part.strip()
                        if name:
                            names.add(name)
                else:
                    walk(item)
        elif isinstance(value, list):
            for item in value:
                walk(item)

    walk(obj)
    return {n for n in names if IMAGE_RE.match(n)}


def count_records(obj) -> int:
    if not isinstance(obj, dict):
        return 0
    return sum(len(v) for v in obj.values() if isinstance(v, list))


class Storage:
    def __init__(self):
        self.root = config.ROOT
        self.data_file = self.root / "data.json"
        self.meta_file = self.root / "meta.json"
        self.images_dir = self.root / "images"
        self.history_dir = self.root / "history"
        self.cache_dir = self.root / "cache"
        self.tmp_dir = self.root / "tmp"
        for d in (self.root, self.images_dir, self.history_dir, self.cache_dir, self.tmp_dir):
            d.mkdir(parents=True, exist_ok=True)
        self.lock = asyncio.Lock()

    # ---------- state ----------
    def _meta(self) -> dict:
        meta = _read_json(self.meta_file, {}) or {}
        if not isinstance(meta, dict):
            meta = {}
        meta.setdefault("revision", 0)
        meta.setdefault("sha256", None)
        meta.setdefault("dataBytes", 0)
        meta.setdefault("receivedAt", 0)
        meta.setdefault("clientExportedAt", 0)
        meta.setdefault("deviceId", "")
        meta.setdefault("deviceName", "")
        meta.setdefault("recordCount", 0)
        meta.setdefault("primaryDeviceId", "")
        meta.setdefault("primaryDeviceName", "")
        return meta

    def _images(self) -> list[str]:
        try:
            return sorted(
                p.name for p in self.images_dir.iterdir()
                if p.is_file() and IMAGE_RE.match(p.name)
            )
        except FileNotFoundError:
            return []

    def state(self) -> dict:
        meta = self._meta()
        images = self._images()
        return {
            "revision": int(meta.get("revision") or 0),
            "sha256": meta.get("sha256"),
            "dataBytes": int(meta.get("dataBytes") or 0),
            "receivedAt": int(meta.get("receivedAt") or 0),
            "clientExportedAt": int(meta.get("clientExportedAt") or 0),
            "deviceId": meta.get("deviceId") or "",
            "deviceName": meta.get("deviceName") or "",
            "recordCount": int(meta.get("recordCount") or 0),
            "primaryDeviceId": config.PRIMARY_DEVICE_ID or meta.get("primaryDeviceId") or "",
            "primaryDeviceName": meta.get("primaryDeviceName") or "",
            "images": images,
            "imageCount": len(images),
        }

    # ---------- primary device ----------
    def _primary_id(self, meta: dict) -> str:
        return config.PRIMARY_DEVICE_ID or (meta.get("primaryDeviceId") or "")

    def check_primary(self, device_id: str) -> None:
        meta = self._meta()
        primary = self._primary_id(meta)
        if primary and device_id != primary:
            raise NotPrimary(primary, meta.get("primaryDeviceName") or "")

    # ---------- images ----------
    async def save_images(self, files: Iterable, device_id: str = "") -> dict:
        self.check_primary(device_id)
        accepted, skipped, rejected = [], [], []
        seen: set[str] = set()
        if len(list(files)) > config.MAX_FILES_PER_REQUEST:
            raise ValueError(f"一次最多上传 {config.MAX_FILES_PER_REQUEST} 张照片")
        for upload in files:
            name = os.path.basename(upload.filename or "")
            if not IMAGE_RE.match(name):
                rejected.append({"name": name or "?", "reason": "文件名不合法"})
                await upload.close()
                continue
            if name in seen:
                skipped.append(name)
                await upload.close()
                continue
            seen.add(name)
            target = self.images_dir / name
            if target.exists():
                skipped.append(name)
                await upload.close()
                continue

            tmp = self.tmp_dir / f"img-{time.time_ns()}-{name}"
            total = 0
            ok = True
            try:
                with open(tmp, "wb") as out:
                    first = True
                    while True:
                        chunk = await upload.read(1024 * 1024)
                        if not chunk:
                            break
                        if first:
                            first = False
                            if not chunk.startswith(JPEG_MAGIC):
                                ok = False
                                break
                        total += len(chunk)
                        if total > config.MAX_IMAGE_BYTES:
                            ok = False
                            break
                        out.write(chunk)
                    out.flush()
                    os.fsync(out.fileno())
                if not ok:
                    rejected.append({"name": name, "reason": "不是有效 JPEG 或文件过大"})
                    tmp.unlink(missing_ok=True)
                    continue
                os.replace(tmp, target)
                accepted.append(name)
            except Exception as e:
                rejected.append({"name": name, "reason": str(e)})
                tmp.unlink(missing_ok=True)
            finally:
                await upload.close()
        return {"accepted": accepted, "skipped": skipped, "rejected": rejected}

    # ---------- data ----------
    async def save_data(
        self,
        raw: bytes,
        device_id: str,
        device_name: str,
        base_revision: int | None,
    ) -> dict:
        if len(raw) > config.MAX_DATA_BYTES:
            raise ValueError("数据文件过大")
        try:
            obj = json.loads(raw.decode("utf-8"))
        except Exception as e:
            raise ValueError(f"JSON 格式不正确: {e}") from e
        if not isinstance(obj, dict) or not isinstance(obj.get("version"), int):
            raise ValueError("缺少 version 字段")

        sha = hashlib.sha256(raw).hexdigest()
        received_at = now_ms()

        async with self.lock:
            meta = self._meta()
            primary = self._primary_id(meta)
            if primary and device_id != primary:
                raise NotPrimary(primary, meta.get("primaryDeviceName") or "")
            if not primary:
                if not device_id:
                    raise ValueError("缺少设备 ID，无法登记主设备")
                primary = device_id
                meta["primaryDeviceId"] = device_id
                meta["primaryDeviceName"] = device_name

            current_rev = int(meta.get("revision") or 0)
            if base_revision is not None and base_revision != current_rev:
                raise RevisionConflict(self.state())

            new_rev = current_rev + 1
            self._archive_history(current_rev, meta)

            _atomic_write(self.data_file, raw)
            new_meta = {
                "revision": new_rev,
                "sha256": sha,
                "dataBytes": len(raw),
                "receivedAt": received_at,
                "clientExportedAt": int(obj.get("exportedAt") or 0),
                "deviceId": device_id[:120],
                "deviceName": device_name[:120],
                "recordCount": count_records(obj),
                "primaryDeviceId": primary,
                "primaryDeviceName": meta.get("primaryDeviceName") or device_name,
            }
            _atomic_write(
                self.meta_file,
                json.dumps(new_meta, ensure_ascii=False, indent=2).encode("utf-8"),
            )
            self._prune_history()

            referenced = collect_photo_names(obj)
            missing = sorted(n for n in referenced if not (self.images_dir / n).exists())
            state = self.state()
            state["imagesMissing"] = missing
            state["referencedImages"] = len(referenced)
            return state

    def _archive_history(self, revision: int, meta: dict) -> None:
        if not self.data_file.exists():
            return
        stamp = time.strftime("%Y%m%d-%H%M%S", time.localtime())
        prefix = f"{stamp}-r{revision}"
        try:
            data = self.data_file.read_bytes()
            _atomic_write(self.history_dir / f"data-{prefix}.json", data)
            _atomic_write(
                self.history_dir / f"meta-{prefix}.json",
                json.dumps(meta, ensure_ascii=False, indent=2).encode("utf-8"),
            )
        except Exception:
            # 历史归档失败不阻断主流程
            pass

    def _prune_history(self) -> None:
        try:
            data_files = sorted(
                self.history_dir.glob("data-*.json"),
                key=lambda p: p.stat().st_mtime,
                reverse=True,
            )
            keep = config.HISTORY_KEEP
            for path in data_files[keep:]:
                path.unlink(missing_ok=True)
                path.with_name(path.name.replace("data-", "meta-", 1)).unlink(missing_ok=True)
        except Exception:
            pass

    # ---------- zip ----------
    def build_backup_zip(self) -> dict | None:
        if not self.data_file.exists():
            return None
        meta = self._meta()
        sha = meta.get("sha256") or hashlib.sha256(self.data_file.read_bytes()).hexdigest()
        target = self.cache_dir / f"backup-{sha[:24]}.zip"
        if target.exists():
            return {
                "path": target,
                "sha256": sha,
                "revision": int(meta.get("revision") or 0),
                "imagesMissing": 0,
            }

        try:
            raw = self.data_file.read_bytes()
            obj = json.loads(raw.decode("utf-8"))
            referenced = collect_photo_names(obj)
            include_all = False
        except Exception:
            raw = self.data_file.read_bytes()
            referenced = set()
            include_all = True

        images = self._images()
        if include_all:
            selected = images
        else:
            selected = [n for n in images if n in referenced]

        missing = 0 if include_all else len([n for n in referenced if n not in set(images)])

        tmp = self.cache_dir / f".tmp-{time.time_ns()}.zip"
        with zipfile.ZipFile(tmp, "w", allowZip64=True) as zf:
            zf.writestr(
                zipfile.ZipInfo("data.json"),
                raw,
                compress_type=zipfile.ZIP_DEFLATED,
            )
            for name in selected:
                path = self.images_dir / name
                if path.is_file():
                    zf.write(path, arcname=f"images/{name}", compress_type=zipfile.ZIP_STORED)
        os.replace(tmp, target)
        self._prune_cache(keep_name=target.name)
        return {
            "path": target,
            "sha256": sha,
            "revision": int(meta.get("revision") or 0),
            "imagesMissing": missing,
        }

    def _prune_cache(self, keep_name: str) -> None:
        try:
            files = sorted(
                self.cache_dir.glob("backup-*.zip"),
                key=lambda p: p.stat().st_mtime,
                reverse=True,
            )
            for path in files:
                if path.name == keep_name:
                    continue
                if len([p for p in files if p.name != keep_name]) > 3:
                    path.unlink(missing_ok=True)
        except Exception:
            pass
