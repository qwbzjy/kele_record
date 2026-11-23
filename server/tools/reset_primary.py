#!/usr/bin/env python3
"""清空服务器记录的主设备，让下一台上传数据的设备成为主设备。

用法（在服务器上，以服务用户执行）：
    sudo -u cola-sync COLA_SYNC_ROOT=/var/lib/cola-sync \
      /opt/cola-sync/venv/bin/python /opt/cola-sync/tools/reset_primary.py

注意：如果 systemd 配置里设置了 COLA_SYNC_PRIMARY_DEVICE_ID，主设备由环境变量
强制指定，本脚本无效。
"""
import json
import os
from pathlib import Path

root = Path(os.environ.get("COLA_SYNC_ROOT", "/var/lib/cola-sync")).resolve()
meta_path = root / "meta.json"

if not meta_path.exists():
    print(f"未找到 {meta_path}，服务器还没有数据")
    raise SystemExit(1)

meta = json.loads(meta_path.read_text(encoding="utf-8"))
meta["primaryDeviceId"] = ""
meta["primaryDeviceName"] = ""

tmp = root / "tmp" / "meta.reset.json"
tmp.parent.mkdir(parents=True, exist_ok=True)
tmp.write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")
os.replace(tmp, meta_path)

print("已清空主设备。下一台成功上传数据的设备将成为主设备。")
