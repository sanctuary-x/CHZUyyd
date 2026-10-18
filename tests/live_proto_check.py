# -*- coding: utf-8 -*-
"""真机协议联调（需要账号）：登录 → 学期 → 记录 → 抓轨迹 → 校验。

用法（不要把密码写进文件）：
    set CHZU_USER=学号
    set CHZU_PASS=密码
    py -3.14 tests\live_proto_check.py
"""
import json
import os
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "python"))

from proto import config, history, login  # noqa: E402
from proto.http import Api  # noqa: E402

user = os.environ.get("CHZU_USER", "")
pwd = os.environ.get("CHZU_PASS", "")
if not user or not pwd:
    print("请设置 CHZU_USER / CHZU_PASS")
    sys.exit(2)

device = config.default_device()
print("设备：", device)
r = login.login(user, pwd, device)
token = r["token"]
print("登录成功，token：%s…（长度 %d）" % (token[:8], len(token)))

api = Api(device, token)
home = json.loads(api.post("/run/getHomeRunInfo", ""))
print("getHomeRunInfo code:", home.get("code"))
cr = (home.get("data") or {}).get("cralist") or []
print("可跑任务：", [(c.get("id"), c.get("raName"), c.get("schoolId")) for c in cr])
assert home.get("code") == 200 and cr and str(cr[0]["schoolId"]) == config.SCHOOL_ID

terms = history.get_terms(api)
print("学期数：", len(terms), "当前：", terms[0]["key"])
runs = history.get_runs(api, terms[0]["value"])
print("本学期记录：", [(x.get("id"), x.get("recordMileage")) for x in runs[:5]])
assert runs, "本学期没有跑步记录"

tmp = tempfile.mkdtemp()
try:
    path, summary = history.fetch_save(api, terms[0]["value"], runs[0]["id"], tmp)
    print("抓取保存：", os.path.basename(path))
    print("摘要：", {k: summary[k] for k in
                    ("recordMileage", "duration", "recodePace", "recodeCadence", "points")})
    data = json.load(open(path, encoding="utf-8"))
    assert str(data["data"]["schoolId"]) == config.SCHOOL_ID
    print("校验收敛：schoolId=106，轨迹点", len(data["data"]["pointsList"]))
finally:
    import shutil
    shutil.rmtree(tmp, ignore_errors=True)

print("LIVE CHECK PASS")
