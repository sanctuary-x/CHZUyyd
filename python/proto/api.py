# -*- coding: utf-8 -*-
"""Chaquopy / PC 共用入口：所有函数返回一行 JSON 字符串。"""
import json

from . import config, history
from .http import Api, ApiError
from .login import login as do_login
from .run import RunCancelled, RunError, RunSession


def _device(device_json):
    if isinstance(device_json, dict):
        return device_json
    try:
        return json.loads(device_json or "{}")
    except Exception:
        return {}


def _ok(**kw):
    out = {"ok": True}
    out.update(kw)
    return json.dumps(out, ensure_ascii=False)


def _fail(msg, need_login=False, need_cred=False, **kw):
    out = {"ok": False, "msg": str(msg),
           "need_login": bool(need_login), "need_cred": bool(need_cred)}
    out.update(kw)
    return json.dumps(out, ensure_ascii=False)


def version():
    return _ok(version="1.0.0", school=config.SCHOOL_NAME,
               school_id=config.SCHOOL_ID, host=config.SCHOOL_HOST)


def school():
    return _ok(name=config.SCHOOL_NAME, school_id=config.SCHOOL_ID,
               host=config.SCHOOL_HOST, login_url=config.SCHOOL_LOGIN_URL)


def login(username, password, device_json):
    try:
        r = do_login(username, password, _device(device_json))
        return _ok(token=r["token"], uuid=r["uuid"], device_id=r["device_id"],
                   device_name=r["device_name"], sys_edition=r["sys_edition"])
    except ApiError as e:
        return _fail(e, need_login=e.need_login, need_cred=e.need_cred)
    except Exception as e:
        return _fail("登录异常：%s" % e, need_login=True, need_cred=True)


def list_terms(token, device_json):
    try:
        return _ok(data=history.get_terms(Api(_device(device_json), token)))
    except Exception as e:
        return _fail(e, need_login=bool(getattr(e, "need_login", False)))


def list_runs(token, device_json, term_value):
    try:
        return _ok(data=history.get_runs(Api(_device(device_json), token), term_value))
    except Exception as e:
        return _fail(e, need_login=bool(getattr(e, "need_login", False)))


def fetch_save(token, device_json, term_value, run_id, tasks_dir):
    try:
        path, summary = history.fetch_save(
            Api(_device(device_json), token), term_value, run_id, tasks_dir)
        return _ok(saved_path=path, summary=summary)
    except Exception as e:
        return _fail(e, need_login=bool(getattr(e, "need_login", False)))


def list_tasks(tasks_dir):
    try:
        return _ok(data=history.list_tasks(tasks_dir))
    except Exception as e:
        return _fail(e)


def run_table(token, device_json, tasks_dir, drift, progress_sink=None):
    """打表跑步。progress_sink 是 Kotlin 对象，需有 onProgress(str) 与 isCancelled()。"""

    def on_progress(info):
        if progress_sink is not None:
            try:
                fn = progress_sink.onProgress
                fn(json.dumps(info, ensure_ascii=False))
            except Exception:
                pass

    def cancelled():
        if progress_sink is None:
            return False
        try:
            v = progress_sink.isCancelled
            return bool(v() if callable(v) else v)
        except Exception:
            return False

    try:
        api = Api(_device(device_json), token)
        result = RunSession(api, tasks_dir, drift=bool(drift),
                            progress=on_progress, cancel=cancelled).run()
        return _ok(**result)
    except RunCancelled:
        return _fail("已取消", cancelled=True)
    except RunError as e:
        return _fail(e, need_login=e.need_login)
    except ApiError as e:
        return _fail(e, need_login=e.need_login, need_cred=e.need_cred)
    except Exception as e:
        return _fail("%s：%s" % (type(e).__name__, e))
