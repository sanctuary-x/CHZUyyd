# -*- coding: utf-8 -*-
"""滁州学院云运动协议常量（本程序仅供滁州学院使用）。"""

SCHOOL_NAME = "滁州学院"
SCHOOL_ID = "106"
SCHOOL_HOST = "https://tiyu.chzu.edu.cn:8010/m-api"
SCHOOL_LOGIN_URL = "appLoginCHZU"

APP_EDITION = "3.6.6"
PLATFORM = "android"
USER_AGENT = "okhttp/4.9.1"

PUBLIC_KEY = ("BDdKFsuBf51UObke1pEgfER17biBg/5r8slqE4s8oOa8lVesWgIUxsRc+AmZ72GcuJ56f7avnyJe3"
              "CJY4n00LU4=")
PRIVATE_KEY = "P3s0+rMuY4Nt5cUWuOCjMhDzVNdom+W0RvdV6ngM+/E="
CIPHER_KEY = "JXhWGZjmhhXN+nt8nLpNxA=="
CIPHER_KEY_ENCRYPTED = (
    "BGfbsG9EkXz5KeCva8E0MisBeS6bhBEDId3VXeIuBoiBMZU0Mosv7PqKsvqxZ3Pjk"
    "Ulsjzh09Se629SWW45XP4TIUeXoLpYzgk5fAMbg0VNVnXuLH9xVzdHAeM+1qJrgvwwkwio85"
    "/DnrP1aArvVQrw3N4xd5tugqQ=="
)
MD5_KEY = "pie0hDSfMRINRXc7s1UIXfkE"

SPLIT_COUNT = 10
MIN_CONSUME = 4.50
MAX_CONSUME = 5.50
STRIDES = 0.8
CADENCE_MIN_OFFSET = 30
CADENCE_MAX_OFFSET = -150

DEVICE_NAMES = [
    "Xiaomi14", "Xiaomi15", "Redmi K70", "HUAWEI Mate 60",
    "OPPO Find X7", "vivo X100", "OnePlus 12", "Honor Magic6",
]


def default_device():
    """生成一组设备信息（与桌面版保持同样的字段与格式）。"""
    import random
    device_id = str(random.randint(1000000000000000, 9999999999999999))
    return {
        "device_id": device_id,
        "uuid": device_id,
        "device_name": random.choice(DEVICE_NAMES),
        "sys_edition": "16",
    }
