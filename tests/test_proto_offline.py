# -*- coding: utf-8 -*-
"""协议层离线测试：不联网，覆盖登录请求结构、历史落盘、打表参数、漂移、加解密。"""
import base64
import gzip
import json
import os
import shutil
import sys
import tempfile
import unittest

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "python"))

from proto import config, crypto, history, login as login_mod, run as run_mod  # noqa: E402
from proto.http import Api  # noqa: E402


class FakeApi:
    device = {"device_id": "1234567890123456", "uuid": "1234567890123456",
              "device_name": "Xiaomi15", "sys_edition": "16"}

    def __init__(self, detail_school="106"):
        self.calls = []
        self.detail_school = detail_school

    def post(self, router, data, is_bytes=False, key_ctx=None):
        self.calls.append((router, data, is_bytes))
        if router == "/run/listXnYearXqByStudentId":
            return json.dumps({"code": 200, "data": [
                {"key": "2026-2027 学年 第一学期", "value": "crs_run_record106",
                 "sjd": "2026.09.01-2027.01.31"}]}, ensure_ascii=False)
        if router == "/run/crsReocordInfoList":
            return json.dumps({"code": 200, "data": {"rank": [
                {"rankList": [{"id": "1", "endTime": "17日 18:31",
                               "recordMileage": 2.07}]}]}}, ensure_ascii=False)
        if router == "/run/crsReocordInfo":
            detail = {"code": 200, "data": {
                "schoolId": self.detail_school, "recordMileage": 2.07,
                "duration": 1026, "recordStartTime": "2026-10-17 18:14:33",
                "recordEndTime": "2026-10-17 18:31:51", "recodePace": 8.25,
                "recodeCadence": 127, "recodeDislikes": 5,
                "pointsList": [{"point": "117.425,32.887", "speed": 3.5,
                                "runMileage": 0, "runTime": 0}],
                "manageList": [{"point": "117.425,32.887", "marked": "Y", "index": 0}],
            }}
            raw = gzip.compress(json.dumps(detail, ensure_ascii=False).encode())
            if key_ctx is not None:
                key_ctx["key"] = config.CIPHER_KEY
            return crypto.encrypt_sm4(raw, base64.b64decode(config.CIPHER_KEY), is_bytes=True)
        if router == "/run/getHomeRunInfo":
            return json.dumps({"code": 200, "data": {"cralist": [{
                "raType": "T3", "id": 361, "schoolId": 106,
                "raRunArea": "school:run:task:106:6150:R0:2:1:361",
                "raDislikes": 5, "raCadenceMin": 35, "raCadenceMax": 350,
            }]}}, ensure_ascii=False)
        if router == "/run/start":
            return json.dumps({"code": 200, "data": {
                "recordStartTime": "2026-10-17 20:00:00", "id": 999,
                "studentId": "TEST"}})
        if router in ("/run/splitPointCheating", "/run/finish"):
            return json.dumps({"code": 200, "msg": "操作成功"})
        raise AssertionError("unexpected router: %s" % router)


def make_task(path, n=25, duration=2, school_id=106):
    points = [{"point": "117.%d,32.%d" % (425000 + i, 887000 + i),
               "speed": 3.5, "runMileage": i * 10, "runTime": i * 1}
              for i in range(n)]
    task = {"msg": "操作成功", "code": 200, "data": {
        "schoolId": school_id, "recordMileage": 2.07, "duration": duration,
        "recodePace": 8.25, "recodeCadence": 127, "recodeDislikes": 5,
        "pointsList": points,
        "manageList": [{"point": points[0]["point"], "marked": "Y", "index": 0}],
    }}
    with open(path, "w", encoding="utf-8") as f:
        json.dump(task, f, ensure_ascii=False)
    return task


class TestCrypto(unittest.TestCase):
    def test_sm4_roundtrip(self):
        key = base64.b64decode(config.CIPHER_KEY)
        for text in ("", "hello", "中文" * 50):
            enc = crypto.encrypt_sm4(text, key)
            self.assertEqual(crypto.decrypt_sm4(enc, key).decode(), text)

    def test_sm2_shape(self):
        enc = base64.b64decode(crypto.encrypt_sm2("hello"))
        self.assertEqual(enc[0], 0x04)
        self.assertGreater(len(enc), 100)
        self.assertNotEqual(crypto.encrypt_sm2("hello"), crypto.encrypt_sm2("hello"))


class TestLoginRequest(unittest.TestCase):
    def test_login_request_structure(self):
        captured = {}

        def fake_post(url, body, headers, timeout=30):
            captured.update(url=url, body=json.loads(body), headers=headers)
            return type("R", (), {"text": json.dumps(
                {"code": 200, "data": {"token": "TOKEN123"}}), "status_code": 200})()

        old = login_mod.http_post
        login_mod.http_post = fake_post
        try:
            r = login_mod.login("20250001", "p@ss", {
                "device_id": "123", "uuid": "123", "device_name": "Xiaomi",
                "sys_edition": "16"})
        finally:
            login_mod.http_post = old

        self.assertEqual(r["token"], "TOKEN123")
        self.assertTrue(captured["url"].endswith("/login/appLoginCHZU"))
        self.assertIn("cipherKey", captured["body"])
        self.assertIn("content", captured["body"])
        self.assertIn("sign", captured["headers"])
        payload = crypto.decrypt_sm4(captured["body"]["content"],
                                     base64.b64decode(config.CIPHER_KEY)).decode()
        obj = json.loads(payload)
        self.assertEqual(obj["schoolId"], "106")
        self.assertEqual(obj["userName"], "20250001")

    def test_login_empty_cred(self):
        with self.assertRaises(Exception):
            login_mod.login("", "", {})


class TestHistory(unittest.TestCase):
    def test_terms_and_runs(self):
        api = FakeApi()
        terms = history.get_terms(api)
        self.assertEqual(terms[0]["value"], "crs_run_record106")
        runs = history.get_runs(api, "crs_run_record106")
        self.assertEqual(runs[0]["id"], "1")

    def test_fetch_and_save(self):
        tmp = tempfile.mkdtemp()
        try:
            api = FakeApi()
            path, summary = history.fetch_save(api, "crs_run_record106", "1", tmp)
            self.assertTrue(os.path.isfile(path))
            self.assertTrue(os.path.basename(path).startswith("tasklist_"))
            self.assertEqual(summary["recordMileage"], 2.07)
            data = json.load(open(path, encoding="utf-8"))
            self.assertEqual(str(data["data"]["schoolId"]), "106")
            # 第二次编号递增
            path2, _ = history.fetch_save(api, "crs_run_record106", "1", tmp)
            self.assertNotEqual(path, path2)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)

    def test_reject_other_school(self):
        tmp = tempfile.mkdtemp()
        try:
            with self.assertRaises(history.HistoryError):
                history.fetch_save(FakeApi(detail_school="158"),
                                   "crs_run_record106", "1", tmp)
            self.assertEqual(os.listdir(tmp), [])
        finally:
            shutil.rmtree(tmp, ignore_errors=True)


class NoSportApi(FakeApi):
    def post(self, router, data, is_bytes=False, key_ctx=None):
        if router == "/run/start":
            return json.dumps({"code": 200, "msg": "操作成功", "data": {
                "canSport": "N",
                "warnContent": "当前时间不在学校规定的跑步时间段内",
            }}, ensure_ascii=False)
        return super().post(router, data, is_bytes=is_bytes, key_ctx=key_ctx)


class TestRunTable(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        make_task(os.path.join(self.tmp, "tasklist_0.json"))

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_payload_and_progress(self):
        api = FakeApi()
        events = []
        session = run_mod.RunSession(api, self.tmp, drift=False,
                                     progress=events.append)
        result = session.run()
        self.assertTrue(result["ok"])
        self.assertEqual(result["points"], 25)
        self.assertTrue(any(e["stage"] == "start" for e in events))
        self.assertEqual(events[-1]["stage"], "uploaded")
        self.assertEqual(events[-1]["sent"], 25)
        self.assertGreaterEqual(len(events), 4)

        splits = [c for c in api.calls if c[0] == "/run/splitPointCheating"]
        self.assertEqual(len(splits), 3)          # 10 + 10 + 5
        body = json.loads(gzip.decompress(splits[0][1]).decode())
        for key in ("StepNumber", "mileage", "runSteps", "cardPointList", "time",
                    "crsRunRecordId", "speeds", "schoolId", "strides", "userName"):
            self.assertIn(key, body)
        self.assertEqual(str(body["schoolId"]), "106")
        finish = [c for c in api.calls if c[0] == "/run/finish"][0]
        body = json.loads(finish[1])
        for key in ("recordMileage", "recodeCadence", "recodePace", "raRunArea",
                    "recodeDislikes", "raId", "id", "duration", "recordStartTime",
                    "manageList", "remake"):
            self.assertIn(key, body)

    def test_reject_other_school_task(self):
        make_task(os.path.join(self.tmp, "tasklist_0.json"), school_id=158)
        session = run_mod.RunSession(FakeApi(), self.tmp, drift=False)
        with self.assertRaises(run_mod.RunError):
            session.load_task()

    def test_cancel(self):
        api = FakeApi()
        flag = {"cancel": False}

        def progress(info):
            flag["cancel"] = True

        session = run_mod.RunSession(api, self.tmp, drift=False,
                                     progress=progress,
                                     cancel=lambda: flag["cancel"])
        with self.assertRaises(run_mod.RunCancelled):
            session.run()

    def test_cannot_start_outside_hours(self):
        session = run_mod.RunSession(NoSportApi(), self.tmp, drift=False)
        with self.assertRaises(run_mod.RunError) as ctx:
            session.run()
        self.assertIn("时间段", str(ctx.exception))

    def test_drift_changes_mileage(self):
        task = make_task(os.path.join(self.tmp, "tasklist_0.json"))
        before = task["data"]["recordMileage"]
        after = run_mod.add_drift(task)
        self.assertEqual(len(after["data"]["pointsList"]), 25)
        self.assertIsInstance(after["data"]["recordMileage"], float)
        self.assertNotEqual(before, after["data"]["recordMileage"])


if __name__ == "__main__":
    unittest.main(verbosity=2)

