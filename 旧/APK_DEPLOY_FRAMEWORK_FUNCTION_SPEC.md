# RunLog APK 部署、框架与功能说明书

更新时间：2026-06-29

本文档记录开源版 RunLog 的 Android 工程结构、功能范围、构建方式和开源清理结果。

## 1. 项目总览

项目根目录：

```text
F:\CIL\yunrun
```

核心组成：

```text
RunLogKotlin      Android App 主工程
旧                项目说明和历史迁移文档
```

当前 APK 输出：

```text
F:\CIL\yunrun\RunLogKotlin\app\build\outputs\apk\debug\app-debug.apk
```

开源地址：

```text
https://github.com/hzjh22/yunrun-apk
```

## 2. 开源免费声明

App 公告页固定显示本地声明，不再请求任何远程公告接口：

```text
本软件完全免费，如果你付费了说明你被骗了。
感觉软件好用的话请点击一个 Star，谢谢。
开源地址：https://github.com/hzjh22/yunrun-apk
```

## 3. Android App 架构

主要源码目录：

```text
RunLogKotlin/app/src/main/java/com/runlog
```

模块划分：

```text
MainActivity.java
  App 主界面和页面调度。包含首页、数据页、我的中心、公告展示、运行进度更新。

capture/
  PCAPdroid 文本导入解析。当前保留手动导入方案，不内置自动抓包。

crypto/
  SM4、摘要、接口加密解密辅助逻辑。

data/
  配置模型、配置存储、配置校验、敏感字段脱敏。

log/
  App 本地日志文件写入和读取。

network/
  登录、历史数据提取、跑步提交、ApiClient 和本地公告模型。

task/
  SQLite 历史数据存储、任务去重、随机/指定选择和使用次数统计。
```

## 4. 页面功能

首页：

- 显示开始跑步、停止跑步、当前任务、长度、时间、配速和运行进度。
- 点击开始跑步后先检测配置完整性，再执行 Android 原生运行流程。
- 停止按钮用于中断当前测试运行。

数据页：

- 支持导入历史 JSON/TXT。
- 支持在线提取历史跑步数据。
- 重复数据按指纹合并，不再重复保存。
- 卡片显示长度、时间、使用次数、出现次数、轨迹点、打卡点和配速。
- 支持“换一组”随机展示 5 条数据。
- 支持“指定使用”某条数据。

我的中心：

- 显示开源免费状态。
- 显示 deviceName、token、deviceId、数据选择模式和草稿状态。
- 不展示 `schoolHost`。
- 支持检测配置、刷新 Token、导入配置、查看公告、切换随机/指定模式、添加漂移、清除历史数据、查看日志。

公告页：

- 使用本地开源声明。
- 不联网，不依赖远程公告接口。

## 5. 构建方式

进入 Android 工程目录：

```powershell
cd F:\CIL\yunrun\RunLogKotlin
```

执行 debug 构建：

```powershell
gradle :app:assembleDebug
```

如果系统没有全局 `gradle`，需要先安装 Gradle 或在本机 Android Studio/IDEA 中使用对应 Gradle 执行同一任务。

## 6. 开源清理结果

本轮开源化已经完成以下清理：

- 移除旧私有访问控制、设备绑定、签名校验和远程公告依赖。
- 删除旧私有模块源码、部署脚本和管理脚本。
- 删除私钥、公钥、签名材料、旧 APK、压缩包、反编译输出和本地验证产物。
- 删除打包进仓库的真实历史跑步 JSON。
- Android 网络安全配置仅保留业务接口所需的 `sports.aiyyd.com` 明文访问例外。

## 7. 不应提交的内容

以下内容不要提交到 GitHub：

- 真实账号、密码、Token、deviceId、uuid。
- 抓包文本、HAR、PCAP/PCAPNG 文件。
- 真实历史跑步轨迹 JSON。
- APK、AAB、签名证书、密钥和 keystore。
- 反编译输出、模拟器镜像、构建缓存、日志和数据库文件。
