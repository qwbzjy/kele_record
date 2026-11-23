import os
from pathlib import Path

TOKEN = os.environ.get("COLA_SYNC_TOKEN", "").strip()
ROOT = Path(os.environ.get("COLA_SYNC_ROOT", "/var/lib/cola-sync")).resolve()
# 可选：显式指定唯一可写的设备 ID；不设置时由第一台上传数据的设备自动成为主设备
PRIMARY_DEVICE_ID = os.environ.get("COLA_SYNC_PRIMARY_DEVICE_ID", "").strip()
HISTORY_KEEP = max(1, int(os.environ.get("COLA_SYNC_HISTORY", "20")))
MAX_IMAGE_BYTES = int(os.environ.get("COLA_SYNC_MAX_IMAGE_MB", "10")) * 1024 * 1024
MAX_REQUEST_BYTES = int(os.environ.get("COLA_SYNC_MAX_REQUEST_MB", "60")) * 1024 * 1024
MAX_DATA_BYTES = int(os.environ.get("COLA_SYNC_MAX_DATA_MB", "20")) * 1024 * 1024
MAX_FILES_PER_REQUEST = int(os.environ.get("COLA_SYNC_MAX_FILES", "30"))
