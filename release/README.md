# CHZUyyd 安装包

| 文件 | 说明 |
|---|---|
| `CHZUyyd-v1.0.0-arm64.apk` | 滁州学院安卓版，arm64-v8a，已签名（GitHub Releases 资产） |
| `SHA256.txt` | APK 校验值 |

## 下载与安装

APK 在仓库的 **Releases → v1.0.0** 中下载：

<https://github.com/sanctuary-x/CHZUyyd/releases/latest>

1. 下载 `CHZUyyd-v1.0.0-arm64.apk`；
2. 传到 Android 手机（微信文件传输 / 数据线 / adb 均可）；
3. 点击 APK，按提示允许“安装未知来源应用”；
4. 安装后桌面出现「云运动·滁州」；
5. 首次启动允许通知权限；到「设置」页按引导授权精确闹钟、电池优化、自启动。

```powershell
adb install CHZUyyd-v1.0.0-arm64.apk
```

## 注意

- 仅支持 arm64-v8a（2018 年后的主流手机）；
- 仓库不含签名私钥，APK 已用本机私钥签名，可直接安装；
- 本程序不内置任何历史轨迹，首次启动需到「历史」页自行抓取；
- 支持单条删除 / 清空全部轨迹；
- 登录会让手机端云运动下线，请减少重复登录；
- 仅供技术交流学习，请遵守学校规定，风险自负。
