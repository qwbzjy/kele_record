# 可乐成长记 · 同步服务

给「可乐成长记」Android App 用的极小同步服务：只保存一份最新 `data.json` + 照片文件，提供版本号防覆盖、历史数据回滚和整包 zip 下载。

- 技术：Python 3.10+ / FastAPI / uvicorn
- 存储：本地文件，无数据库
- 鉴权：`Authorization: Bearer <token>`（token 由环境变量提供）
- 默认监听 `127.0.0.1:8017`，由 nginx 对外提供 HTTP/HTTPS

## 目录结构

```
server/
  app/{main.py,storage.py,config.py}
  requirements.txt
  deploy/cola-sync.service
  deploy/nginx.conf.example
```

服务器上的数据目录（默认 `/var/lib/cola-sync`）：

```
data.json         最新备份（BackupData JSON）
meta.json         revision / sha256 / 来源设备 / 时间 / 记录数
images/           照片（文件名 UUID.jpg，只增不删）
history/          最近 20 份 data.json + meta.json
cache/            下载用 zip 缓存
tmp/              原子写临时文件
```

## Ubuntu/Debian 部署（HTTP 起步）

以下命令在腾讯云 CVM 上用 `root` 执行；请把 `<TOKEN>` 换成自己生成的值。

### 1. 安装依赖与用户

```bash
apt update
apt install -y python3 python3-venv nginx

useradd --system --home /opt/cola-sync --shell /usr/sbin/nologin cola-sync
mkdir -p /opt/cola-sync /var/lib/cola-sync
chown -R cola-sync:cola-sync /opt/cola-sync /var/lib/cola-sync
chmod 700 /var/lib/cola-sync
```

### 2. 上传代码并安装 Python 依赖

把本目录 `server/` 的内容传到 `/opt/cola-sync/`（要求 `/opt/cola-sync/app/main.py` 存在），然后：

```bash
cd /opt/cola-sync
python3 -m venv venv
./venv/bin/pip install --upgrade pip
./venv/bin/pip install -r requirements.txt
chown -R cola-sync:cola-sync /opt/cola-sync
```

> 国内服务器 pip 慢时加镜像：
> `./venv/bin/pip install -r requirements.txt -i https://mirrors.aliyun.com/pypi/simple`

### 3. 生成 Token 与配置

```bash
TOKEN=$(openssl rand -hex 32)
printf 'COLA_SYNC_TOKEN=%s\nCOLA_SYNC_ROOT=/var/lib/cola-sync\nCOLA_SYNC_HISTORY=20\n' "$TOKEN" > /etc/cola-sync.env
chmod 600 /etc/cola-sync.env
cat /etc/cola-sync.env     # 记下 Token，填到 App 里

# 可选：固定主设备（填 App 里显示的设备 ID）；不填则第一台上传的设备自动成为主设备
# echo 'COLA_SYNC_PRIMARY_DEVICE_ID=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx' >> /etc/cola-sync.env
```

### 4. systemd

```bash
cp /opt/cola-sync/deploy/cola-sync.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now cola-sync
systemctl status cola-sync
curl -s http://127.0.0.1:8017/api/v1/health
```

### 5. nginx 反向代理（HTTP）

```bash
cp /opt/cola-sync/deploy/nginx.conf.example /etc/nginx/sites-available/cola-sync
ln -sf /etc/nginx/sites-available/cola-sync /etc/nginx/sites-enabled/cola-sync
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx
```

腾讯云控制台/轻量服务器防火墙放行 **80** 端口（如用 HTTPS 再加 443）。不要对公网开放 8017。

测试（把 IP 换成你的公网 IP）：

```bash
curl http://<公网IP>/api/v1/health
curl -H "Authorization: Bearer <TOKEN>" http://<公网IP>/api/v1/state
```

### 6. App 配置

「我的 → 云端同步」：

- 服务器地址：`http://<公网IP>`（nginx 80 端口；若直接开 8000 则写 `http://<IP>:8000`）
- Token：上面生成的 32 字节 hex
- 设备名：默认机型，可改
- 仅 Wi-Fi：联调时可关闭
- **本机为主设备**：只有主设备能录入和上传；其他设备关闭此开关后变为只读（只能查看、从云端恢复）

### 6.1 主设备限制

- 服务端维护一个 `primaryDeviceId`：**第一台成功上传数据的设备自动成为主设备**。
- 之后其他设备的 `POST /data`、`POST /images` 一律返回 `403 not_primary`。
  App 收到 403 会自动把本机切换为只读，并提示云端主设备名称。
- 主设备的设备 ID 会显示在 App 的「云端同步」卡片里；服务端 `/state` 也会返回
  `primaryDeviceId` / `primaryDeviceName`。
- 需要更换主设备时，二选一：
  1. 在服务器上清空主设备，下一台上传的设备自动接管：
     ```bash
     sudo -u cola-sync COLA_SYNC_ROOT=/var/lib/cola-sync \
       /opt/cola-sync/venv/bin/python /opt/cola-sync/tools/reset_primary.py
     systemctl restart cola-sync
     ```
     然后在新的主设备 App 中打开「本机为主设备」并点「立即上传」。
  2. 在 `/etc/cola-sync.env` 中显式指定设备 ID，然后重启服务：
     ```
     COLA_SYNC_PRIMARY_DEVICE_ID=<新主设备的设备 ID>
     systemctl restart cola-sync
     ```
- 只读设备仍然可以调用 `GET /state` 和 `GET /backup.zip`，正常「从云端恢复」。
- 如果希望一开始就固定主设备，可在部署时设置 `COLA_SYNC_PRIMARY_DEVICE_ID`，
  避免第一台误连的手机抢占主设备。

## 临时直连测试（不装 nginx）

```bash
cd /opt/cola-sync
COLA_SYNC_TOKEN=dev-token COLA_SYNC_ROOT=/tmp/cola-sync \
  ./venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000
```

放行 8000 端口后，App 填 `http://<公网IP>:8000`。**测试完请关闭端口并改用 nginx/HTTPS。**

## 升级 HTTPS（强烈建议）

前提：域名 + DNS 解析到服务器。大陆地域需要域名备案；香港/新加坡地域无需备案。

```bash
apt install -y certbot python3-certbot-nginx
certbot --nginx -d sync.example.com
```

然后把 `deploy/nginx.conf.example` 里 443 的 server 块启用（或由 certbot 自动改写）。App 中地址改为 `https://sync.example.com`，此时不会再有“明文 HTTP”警告。

## 安全与运维

- Token 等同密码：不要提交到 Git、不要发在公开群；泄露后立即修改 `/etc/cola-sync.env` 并 `systemctl restart cola-sync`。
- 服务器照片只增不减（历史快照可能引用）；需要回收空间时先备份 `/var/lib/cola-sync`，再人工清理 `images/` 中不再被引用的文件。
- `history/` 默认保留最近 20 份 `data.json`/`meta.json`，可通过 `COLA_SYNC_HISTORY` 调整。
- 建议定期备份整个 `/var/lib/cola-sync`（例如 `tar czf` 到另一块盘或对象存储）。
- release 版 App 默认只信任 HTTPS；如必须使用 HTTP，请阅读 App 的 `network_security_config.xml` 说明。

## API 速览

```
GET  /api/v1/health                 免鉴权
GET  /api/v1/state                  云端 revision / 设备 / 图片列表
POST /api/v1/images                 multipart files（只传缺失图片）
POST /api/v1/data                   BackupData JSON；X-Base-Revision 防并发覆盖
GET  /api/v1/backup.zip             data.json + 照片整包
```

`POST /api/v1/data` 不带 `X-Base-Revision` 表示强制覆盖（App 只在用户确认“以本机覆盖云端”时使用）；带错误版本号返回 409 和最新 state，App 会进入冲突提示而不是自动覆盖。
