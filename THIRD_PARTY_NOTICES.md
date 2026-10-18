# 第三方组件说明

## 本项目自身的许可

本项目（CHZUyyd Android）整体以 **GNU 通用公共许可证第 3 版或其后续版本**
（**GPL-3.0-or-later**）发布，全文见 [LICENSE](LICENSE)。

下面的第三方组件均与 GPL-3.0 兼容：MIT 与 Apache-2.0 都可以并入 GPL-3.0 的
作品（Apache-2.0 与 GPL-3.0 明确兼容），因此本项目可以整体按 GPL-3.0 分发。
各组件仍保留其原始许可证与版权声明。

## 组件清单

本项目包含以下第三方组件，其版权归原作者所有：

| 组件 | 用途 | 许可证 |
|---|---|---|
| gmssl（`python/proto/vendor/gmssl` 子集） | SM2/SM3/SM4 纯 Python 实现 | MIT |
| Chaquopy | Android 内嵌 Python 运行时/构建插件 | MIT |
| AndroidX / Jetpack Compose / Material 3 | Android UI 与基础库 | Apache-2.0 |
| Kotlin / Gradle | 构建工具链 | Apache-2.0 / 各自许可证 |

`gmssl` 原始项目：<https://github.com/duanhongyi/gmssl>

本项目未修改 gmssl 的算法实现，仅复制 `sm2/sm3/sm4/func` 四个纯 Python 文件，
并对 `sm2.py` 的 `Cryptodome.Util.asn1` 导入做了可选处理（Android 上不需要
签名/验签，因此该依赖可缺省）。
