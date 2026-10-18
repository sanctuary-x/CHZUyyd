# -*- coding: utf-8 -*-
"""SM2 / SM4 / MD5（纯 Python，使用随包附带的 gmssl 子集）。

与桌面版 scripts/main.py 的加解密实现逐行对齐；不依赖 pycryptodome(x)。
"""
import base64
import hashlib

from .vendor.gmssl import sm2, func
from .vendor.gmssl.sm4 import CryptSM4, SM4_ENCRYPT, SM4_DECRYPT

from . import config


def bytes_to_hex(data):
    return hex(int.from_bytes(data, "big"))[2:].upper()


_sm2 = sm2.CryptSM2(
    public_key=bytes_to_hex(base64.b64decode(config.PUBLIC_KEY)[1:]),
    private_key=bytes_to_hex(base64.b64decode(config.PRIVATE_KEY)),
    mode=1,
    asn1=True,
)


def encrypt_sm2(text):
    """SM2 加密：与桌面版一致，返回 04 || C1C3C2 的 Base64。"""
    info = _sm2.encrypt(text.encode("utf-8"))
    return base64.b64encode(bytes.fromhex("04" + info.hex())).decode()


def encrypt_sm4(value, key, is_bytes=False):
    """SM4-ECB/PKCS7 加密（gmssl 内部处理 PKCS7），返回 Base64 字符串。"""
    c = CryptSM4()
    c.set_key(key, SM4_ENCRYPT)
    raw = value if is_bytes else value.encode("utf-8")
    return base64.b64encode(c.crypt_ecb(raw)).decode()


def decrypt_sm4(value, key):
    c = CryptSM4()
    c.set_key(key, SM4_DECRYPT)
    return c.crypt_ecb(base64.b64decode(value))


def md5(text):
    return hashlib.md5(text.encode("utf-8")).hexdigest()


def pkcs7_pad(data, block_size=16):
    """PKCS#7 填充（保留给调用方；gmssl 的 crypt_ecb 已内部处理）。"""
    n = block_size - len(data) % block_size
    return data + bytes([n]) * n


def pkcs7_unpad(data):
    if not data:
        return data
    n = data[-1]
    if 1 <= n <= 16 and data[-n:] == bytes([n]) * n:
        return data[:-n]
    return data


def random_hex(n):
    return func.random_hex(n)
