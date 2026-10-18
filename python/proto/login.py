# -*- coding: utf-8 -*-
"""登录：与桌面版 scripts/tools/Login.py 的 login_with_ini 对齐。"""
import base64
import json
import time

from . import config, crypto
from .http import ApiError, http_post


def login(username, password, device):
    username = (username or "").strip()
    password = password or ""
    if not username or not password:
        raise ApiError("账号或密码为空，请填写后再登录。", need_cred=True)

    uuid = device.get("uuid") or device.get("device_id") or ""
    device_id = device.get("device_id") or uuid
    device_name = device.get("device_name") or "Xiaomi"
    sys_edition = str(device.get("sys_edition") or "16")

    utc = str(int(time.time()))
    sign = crypto.md5(
        "platform=android&utc=%s&uuid=%s&appsecret=%s"
        % (utc, uuid, config.MD5_KEY)
    )
    # 字段顺序与桌面版一致
    payload = ('{"password":%s,"schoolId":"%s","userName":%s,"type":"1"}'
               % (json.dumps(password, ensure_ascii=False),
                  config.SCHOOL_ID,
                  json.dumps(username, ensure_ascii=False)))
    content = crypto.encrypt_sm4(payload, base64.b64decode(config.CIPHER_KEY))
    body = {"cipherKey": config.CIPHER_KEY_ENCRYPTED, "content": content}
    headers = {
        "token": "",
        "isApp": "app",
        "deviceId": uuid,
        "deviceName": device_name,
        "version": config.APP_EDITION,
        "platform": config.PLATFORM,
        "uuid": uuid,
        "utc": utc,
        "sign": sign,
        "Content-Type": "application/json; charset=utf-8",
        "Accept-Encoding": "gzip",
        "User-Agent": "okhttp/3.12.0",
    }
    url = config.SCHOOL_HOST.rstrip("/") + "/login/" + config.SCHOOL_LOGIN_URL
    resp = http_post(url, json.dumps(body), headers)

    try:
        if "{" in resp.text:
            obj = json.loads(resp.text)
        else:
            obj = json.loads(
                crypto.decrypt_sm4(resp.text, base64.b64decode(config.CIPHER_KEY)).decode()
            )
    except Exception:
        raise ApiError("服务器返回无法解析（可能学校接口变更）：%s" % resp.text[:200])

    data = obj.get("data")
    if not isinstance(data, dict) or "token" not in data:
        msg = obj.get("msg") or json.dumps(obj, ensure_ascii=False)
        raise ApiError("登录被服务器拒绝：%s" % msg, need_cred=True)

    return {
        "token": data["token"],
        "uuid": uuid,
        "device_id": device_id,
        "device_name": device_name,
        "sys_edition": sys_edition,
    }
