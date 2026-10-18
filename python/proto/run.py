# -*- coding: utf-8 -*-
"""打表跑步：开始 → 分片上传 → 结束（与桌面版 main.py 的 table 模式对齐）。"""
import gzip
import json
import math
import os
import random
import time

from . import config
from .http import ApiError, is_auth_error


class RunError(Exception):
    def __init__(self, msg, need_login=False):
        super().__init__(msg)
        self.need_login = need_login


class RunCancelled(Exception):
    pass


def _haversine(lat1, lon1, lat2, lon2):
    r = 6371000
    lat1, lon1, lat2, lon2 = map(math.radians, [lat1, lon1, lat2, lon2])
    dlat = lat2 - lat1
    dlon = lon2 - lon1
    a = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return r * 2 * math.asin(math.sqrt(a))


def add_drift(task_map):
    """与桌面临时工具 tools/drift.py 完全一致的轨迹漂移。"""
    drift = random.uniform(-0.000000001, 0.000000001)
    points = task_map.get("data", {}).get("pointsList", [])
    lon, lat = [], []
    for p in points:
        x, y = map(float, p["point"].split(","))
        lon.append(x + drift)
        lat.append(y + drift)
    dist = 0.0
    for i in range(len(lon) - 1):
        dist += _haversine(lat[i], lon[i], lat[i + 1], lon[i + 1])
    task_map["data"]["recordMileage"] = round(dist / 10) / 100
    for i in range(len(points)):
        points[i]["point"] = "%s,%s" % (lon[i], lat[i])
    return task_map


def _parse(text):
    try:
        return json.loads(text)
    except Exception:
        return None


def _raise_for(obj, what):
    if not isinstance(obj, dict):
        raise RunError("%s失败：服务器返回格式异常" % what)
    if obj.get("code") != 200:
        msg = obj.get("msg", "未知错误")
        raise RunError("%s失败：%s" % (what, msg), need_login=is_auth_error(obj))
    return obj


class RunSession:
    def __init__(self, api, tasks_dir, drift=True, progress=None, cancel=None):
        self.api = api
        self.tasks_dir = tasks_dir
        self.drift = bool(drift)
        self.progress = progress
        self.cancel = cancel or (lambda: False)
        self.task_map = None
        self.raType = None
        self.raId = None
        self.raRunArea = None
        self.raDislikes = 0
        self.raMinDislikes = 0
        self.raCadenceMin = 35
        self.raCadenceMax = 350
        self.schoolId = config.SCHOOL_ID
        self.recordStartTime = ""
        self.crsRunRecordId = 0
        self.userName = ""

    # ---------------------------------------------------------------- 进度
    def _report(self, sent, total, duration, t0, stage):
        if callable(self.progress):
            try:
                self.progress({
                    "sent": int(sent),
                    "total": int(total),
                    "duration": float(duration),
                    "elapsed": max(time.time() - t0, 0.0),
                    "stage": stage,
                })
            except Exception:
                pass

    def _sleep(self, seconds, sent, total, duration, t0):
        end = time.time() + max(float(seconds), 0.0)
        while True:
            if self.cancel():
                raise RunCancelled()
            left = end - time.time()
            if left <= 0:
                break
            time.sleep(min(1.0, left))
            self._report(sent, total, duration, t0, "running")
        self._report(sent, total, duration, t0, "running")

    # ---------------------------------------------------------------- 任务
    def load_task(self):
        if not os.path.isdir(self.tasks_dir):
            raise RunError("没有可用的历史轨迹，请先在「历史」页抓取一条记录。")
        files = [n for n in os.listdir(self.tasks_dir)
                 if n.startswith("tasklist_") and n.endswith(".json")]
        if not files:
            raise RunError("没有可用的历史轨迹，请先在「历史」页抓取一条记录。")
        path = os.path.join(self.tasks_dir, random.choice(files))
        with open(path, "r", encoding="utf-8") as f:
            task = json.load(f)
        data = task.get("data") or {}
        if str(data.get("schoolId", "")) != config.SCHOOL_ID:
            raise RunError("这条轨迹不属于滁州学院（schoolId=%s），已拒绝使用。"
                           % data.get("schoolId", "(空)"))
        if not data.get("pointsList"):
            raise RunError("轨迹文件为空：%s" % os.path.basename(path))
        if self.drift:
            task = add_drift(task)
        self.task_map = task

    def home(self):
        raw = self.api.post("/run/getHomeRunInfo", "")
        obj = _parse(raw)
        _raise_for(obj, "获取跑步任务")
        cralist = (obj.get("data") or {}).get("cralist") or []
        if not cralist:
            raise RunError("当前账号没有可用的跑步任务，请稍后再试。")
        data = cralist[0]
        if str(data.get("schoolId", "")) != config.SCHOOL_ID:
            raise RunError("本程序仅供滁州学院使用：服务器返回的学校 ID 为 %s（应为 %s）。"
                           % (data.get("schoolId", "(空)"), config.SCHOOL_ID))
        self.raType = data["raType"]
        self.raId = data["id"]
        # 与桌面版一致：schoolId 必须是数字（服务端按数字做权限校验）
        self.schoolId = data["schoolId"]
        self.raRunArea = data["raRunArea"]
        self.raDislikes = int(data.get("raDislikes") or 0)
        self.raMinDislikes = self.raDislikes
        self.raCadenceMin = int(data["raCadenceMin"]) + config.CADENCE_MIN_OFFSET
        self.raCadenceMax = int(data["raCadenceMax"]) + config.CADENCE_MAX_OFFSET

    def start(self):
        body = json.dumps({
            "raRunArea": self.raRunArea,
            "raType": self.raType,
            "raId": self.raId,
        })
        obj = _parse(self.api.post("/run/start", body))
        _raise_for(obj, "创建跑步任务")
        d = obj.get("data") or {}
        if d.get("canSport") == "N":
            raise RunError(d.get("warnContent") or "当前时间不在学校规定的跑步时间段内")
        self.recordStartTime = d.get("recordStartTime", "")
        self.crsRunRecordId = d.get("id", 0)
        self.userName = d.get("studentId", "")
        if not self.crsRunRecordId:
            raise RunError(d.get("warnContent") or "创建跑步任务失败：服务器未返回记录 ID（%s）"
                           % obj.get("msg", ""))

    def split(self, points):
        data = {
            "StepNumber": int(float(points[-1]["runMileage"]) - float(points[0]["runMileage"]))
                          / config.STRIDES,
            "a": 0,
            "b": None,
            "c": None,
            "mileage": float(points[-1]["runMileage"]) - float(points[0]["runMileage"]),
            "orientationNum": 0,
            "runSteps": random.uniform(self.raCadenceMin, self.raCadenceMax),
            "cardPointList": points,
            "simulateNum": 0,
            "time": float(points[-1]["runTime"]) - float(points[0]["runTime"]),
            "crsRunRecordId": self.crsRunRecordId,
            "speeds": self.task_map["data"]["recodePace"],
            "schoolId": self.schoolId,
            "strides": config.STRIDES,
            "userName": self.userName,
        }
        raw = self.api.post(
            "/run/splitPointCheating",
            gzip.compress(json.dumps(data).encode("utf-8")),
            is_bytes=True,
        )
        _raise_for(_parse(raw), "上传轨迹点")
        return raw

    def finish(self):
        d = self.task_map["data"]
        body = json.dumps({
            "recordMileage": d["recordMileage"],
            "recodeCadence": d["recodeCadence"],
            "recodePace": d["recodePace"],
            "deviceName": self.api.device["device_name"],
            "sysEdition": self.api.device["sys_edition"],
            "appEdition": config.APP_EDITION,
            "raIsStartPoint": "Y",
            "raIsEndPoint": "Y",
            "raRunArea": self.raRunArea,
            "recodeDislikes": str(d.get("recodeDislikes", "0")),
            "raId": str(self.raId),
            "raType": self.raType,
            "id": str(self.crsRunRecordId),
            "duration": d["duration"],
            "recordStartTime": self.recordStartTime,
            "manageList": d.get("manageList", []),
            "remake": "1",
        })
        obj = _parse(self.api.post("/run/finish", body))
        _raise_for(obj, "结束跑步")
        return obj

    # ---------------------------------------------------------------- 主流程
    def run(self):
        self.home()
        self.load_task()
        self.start()

        points_list = self.task_map["data"]["pointsList"]
        total = max(len(points_list), 1)
        duration = float(self.task_map["data"].get("duration") or 0)
        t0 = time.time()
        self._report(0, total, duration, t0, "start")

        points, count, sent = [], 0, 0
        for point in points_list:
            if self.cancel():
                raise RunCancelled()
            points.append({
                "point": point["point"],
                "runStatus": "1",
                "speed": point["speed"],
                "isFence": "Y",
                "isMock": False,
                "runMileage": point["runMileage"],
                "runTime": point["runTime"],
                "ts": str(int(time.time())),
            })
            count += 1
            if count == config.SPLIT_COUNT:
                self.split(points)
                sent += count
                self._sleep(duration / total * config.SPLIT_COUNT,
                            sent, total, duration, t0)
                count, points = 0, []
        if count:
            self.split(points)
            sent += count
        self._report(sent, total, duration, t0, "uploaded")
        obj = self.finish()
        return {
            "ok": True,
            "code": obj.get("code"),
            "msg": "跑步完成（code=%s）" % obj.get("code"),
            "mileage": self.task_map["data"].get("recordMileage"),
            "duration": duration,
            "points": total,
        }
