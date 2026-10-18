# -*- coding: utf-8 -*-
"""历史跑步数据：学期 → 记录 → 轨迹 → 保存到 tasks_CHZU。"""
import base64
import gzip
import json
import os

from . import config, crypto
from .http import ApiError, is_auth_error


class HistoryError(Exception):
    def __init__(self, msg, need_login=False):
        super().__init__(msg)
        self.need_login = need_login


def _ensure_ok(obj, what):
    if not isinstance(obj, dict):
        raise HistoryError("%s失败：服务器返回格式异常" % what)
    if obj.get("code") != 200:
        msg = obj.get("msg", "未知错误")
        raise HistoryError("%s失败：%s" % (what, msg), need_login=is_auth_error(obj))
    return obj.get("data")


def get_terms(api):
    text = api.post("/run/listXnYearXqByStudentId", "")
    try:
        obj = json.loads(text)
    except Exception:
        raise HistoryError("获取学期列表失败：%s" % text[:200])
    return _ensure_ok(obj, "获取学期列表") or []


def get_runs(api, term_value):
    text = api.post("/run/crsReocordInfoList", json.dumps({"tableName": term_value}))
    try:
        obj = json.loads(text)
    except Exception:
        raise HistoryError("获取跑步记录失败：%s" % text[:200])
    data = _ensure_ok(obj, "获取跑步记录") or {}
    runs = []
    for month in data.get("rank", []) or []:
        runs.extend(month.get("rankList", []) or [])
    return runs


def _next_task_path(tasks_dir):
    os.makedirs(tasks_dir, exist_ok=True)
    last = 0
    for name in os.listdir(tasks_dir):
        if name.startswith("tasklist_") and name.endswith(".json"):
            try:
                num = int(name.replace("tasklist_", "").replace(".json", ""))
                last = max(last, num + 1)
            except ValueError:
                pass
    return os.path.join(tasks_dir, "tasklist_%d.json" % last)


def fetch_save(api, term_value, run_id, tasks_dir):
    key_ctx = {}
    try:
        inner = api.post(
            "/run/crsReocordInfo",
            json.dumps({"id": run_id, "tableName": term_value}),
            key_ctx=key_ctx,
        )
        key = base64.b64decode(key_ctx.get("key") or config.CIPHER_KEY)
        text = gzip.decompress(crypto.decrypt_sm4(inner, key)).decode()
        detail = json.loads(text)
    except Exception as e:
        raise HistoryError("获取或解析详细记录失败：%s" % e)

    if not isinstance(detail, dict) or detail.get("code") != 200:
        msg = detail.get("msg", "未知错误") if isinstance(detail, dict) else "格式异常"
        raise HistoryError("获取详细记录失败：%s" % msg, need_login=is_auth_error(detail))

    data = detail.get("data") or {}
    school_id = str(data.get("schoolId", ""))
    if school_id != config.SCHOOL_ID:
        raise HistoryError(
            "本程序仅供滁州学院使用：这条记录的学校 ID 为 %s（应为 %s），已拒绝保存。"
            % (school_id or "(空)", config.SCHOOL_ID)
        )

    target = _next_task_path(tasks_dir)
    with open(target, "w", encoding="utf-8") as f:
        f.write(text)

    return target, {
        "saved_path": target,
        "recordStartTime": data.get("recordStartTime", ""),
        "recordEndTime": data.get("recordEndTime", ""),
        "recordMileage": data.get("recordMileage", "0"),
        "recodePace": data.get("recodePace", "0"),
        "recodeCadence": data.get("recodeCadence", "0"),
        "duration": data.get("duration", "0"),
        "recodeDislikes": data.get("recodeDislikes", "0"),
        "points": len(data.get("pointsList", []) or []),
    }


def list_tasks(tasks_dir):
    if not os.path.isdir(tasks_dir):
        return []
    out = []
    for name in sorted(os.listdir(tasks_dir)):
        if name.startswith("tasklist_") and name.endswith(".json"):
            p = os.path.join(tasks_dir, name)
            try:
                with open(p, "r", encoding="utf-8") as f:
                    data = (json.load(f) or {}).get("data") or {}
                out.append({
                    "name": name,
                    "mileage": data.get("recordMileage", "0"),
                    "duration": data.get("duration", "0"),
                    "points": len(data.get("pointsList", []) or []),
                    "size": os.path.getsize(p),
                })
            except Exception:
                continue
    return out
