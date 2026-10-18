# -*- coding: utf-8 -*-
"""HTTP 会话：用标准库 urllib 复刻桌面版 pycurl 的请求语义。

* 关闭证书校验（与桌面版 SSL_VERIFYPEER=0 / SSL_VERIFYHOST=0 一致）
* 手动处理 gzip（urllib 不会自动解压）
* 非 2xx 也把响应体返回给上层（桌面版 pycurl 同样会拿到 401 的 JSON）
"""
import gzip
import json
import ssl
import time
import urllib.error
import urllib.request

from . import config
from . import crypto


class ApiError(Exception):
    """协议/网络错误。need_login/need_cred 供 UI 提示重新登录。"""

    def __init__(self, msg, need_login=False, need_cred=False):
        super().__init__(msg)
        self.need_login = need_login
        self.need_cred = need_cred


class Response:
    def __init__(self, text, status_code):
        self.text = text
        self.status_code = status_code


def http_post(url, body, headers, timeout=30):
    data = body.encode("utf-8") if isinstance(body, str) else body
    req = urllib.request.Request(url, data=data, headers=headers, method="POST")
    ctx = ssl._create_unverified_context()
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=ctx) as resp:
            raw = resp.read()
            status = resp.getcode()
    except urllib.error.HTTPError as e:
        raw = e.read()
        status = e.code
    except Exception as e:  # 网络/SSL/DNS
        raise ApiError("网络请求失败：%s" % e)

    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    return Response(raw.decode("utf-8", "ignore"), status)


class Api:
    """云运动接口客户端（学校固定滁州学院）。"""

    def __init__(self, device=None, token=""):
        d = dict(device or {})
        default = config.default_device()
        self.device = {
            "device_id": d.get("device_id") or default["device_id"],
            "uuid": d.get("uuid") or d.get("device_id") or default["uuid"],
            "device_name": d.get("device_name") or default["device_name"],
            "sys_edition": str(d.get("sys_edition") or "16"),
        }
        self.token = token or ""

    @property
    def key(self):
        import base64
        return base64.b64decode(config.CIPHER_KEY)

    def _sign(self, utc):
        return crypto.md5(
            "platform=android&utc=%s&uuid=%s&appsecret=%s"
            % (utc, self.device["uuid"], config.MD5_KEY)
        )

    def _headers(self, token=None):
        utc = str(int(time.time()))
        return {
            "token": token if token is not None else self.token,
            "isApp": "app",
            "deviceId": self.device["device_id"],
            "deviceName": self.device["device_name"],
            "version": config.APP_EDITION,
            "platform": config.PLATFORM,
            "Content-Type": "application/json; charset=utf-8",
            "Connection": "Keep-Alive",
            "Accept-Encoding": "gzip",
            "User-Agent": config.USER_AGENT,
            "utc": utc,
            "uuid": self.device["uuid"],
            "sign": self._sign(utc),
        }

    def post(self, router, data, is_bytes=False, key_ctx=None):
        """加密 POST，返回解密后的文本；解密失败时返回原始文本（同桌面版）。"""
        key = self.key
        body = {
            "cipherKey": crypto.encrypt_sm2(config.CIPHER_KEY),
            "content": crypto.encrypt_sm4(data, key, is_bytes=is_bytes),
        }
        if isinstance(key_ctx, dict):
            key_ctx["key"] = config.CIPHER_KEY
        resp = http_post(config.SCHOOL_HOST + router, json.dumps(body), self._headers())
        try:
            return crypto.decrypt_sm4(resp.text, key).decode()
        except Exception:
            return resp.text

    def post_json(self, router, data, **kw):
        text = self.post(router, data, **kw)
        try:
            return json.loads(text), text
        except Exception:
            raise ApiError("服务器返回无法解析：%s" % text[:200])


def is_auth_error(obj):
    if not isinstance(obj, dict):
        return False
    msg = str(obj.get("msg", ""))
    code = obj.get("code")
    return code in (401, 403) or ("token" in msg.lower()) or ("登录" in msg) \
        or ("失效" in msg) or ("未授权" in msg)
