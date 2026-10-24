# RunLog

本项目完全使用 AI Coding 完成

RunLog 是一个 Android 原生跑步数据管理与运行测试工具。

本软件完全免费，如果你付费了说明你被骗了。感觉软件好用的话请点击一个 Star，谢谢。

开源地址：https://github.com/hzjh22/yunrun-apk

## 直接下载

已编译好的安装包在 Releases 页面：

https://github.com/hzjh22/yunrun-apk/releases

> **请下载最新版本。** 服务端会定期提高最低版本要求，旧版本会报
> `code=911 当前版本过低，请升级！` 而无法读取数据。

## 功能

- 首页显示开始跑步、停止跑步、当前数据、长度、时间、配速和运行进度。
- 数据页支持导入历史 JSON/TXT、在线提取历史数据、重复数据合并、随机显示一组数据、指定某条数据运行。
- **数据页显示本学期跑步次数**（次数 / 目标、里程、学期名），进入页面自动同步，也可手动刷新。
- 我的中心支持配置检测、账号登录刷新 Token、导入抓包配置、切换随机/指定模式、清除本地历史数据、查看运行日志。
- 公告页使用本地开源声明，不依赖远程公告接口。

## 版本适配（重要）

云运动服务端会周期性提高可接受的最低客户端版本。不匹配时接口仍返回 HTTP 200，
但响应体里是 `code=911 "当前版本过低，请升级！"`。

对应关系记录如下：

| 时间 | 服务端要求 | 说明 |
|---|---|---|
| 2026-09 之前 | 3.6.2 | 仓库原始默认值 |
| 2026-09-19 | 3.6.6 | 3.6.2 被拒（`code=500`） |
| 2026-10-20 起 | **3.6.7**（versionCode 65） | 3.6.6 被拒（`code=911`） |

### 自动适配（已内置）

**当前版本已内置自适应，正常情况无需任何手动操作。**

服务端在 911 响应里直接说明它要哪个版本：

```json
{"code":911, "msg":"当前版本过低，请升级！",
 "data":{"edtitionName":"3.6.7", "appEdtitionNumber":"65"}}
```

`ApiClient` 的逻辑：

1. 用当前版本发送请求；
2. 若收到 `code=911`，读取 `data.edtitionName`（缺失时回退到 `appEdtitionNumber` 推算）；
3. 用服务端指定版本**重试一次**；
4. 学到的版本写入内存（全进程共享）并持久化，**后续请求和下次启动都不再触发重试**。

**只会重试一次**。若重试后仍被拒，或服务端返回的版本号与当前相同（说明重试无意义），
直接抛出带明确信息的错误，不会死循环：

```
当前版本过低，请升级！（服务端要求 3.7.0，当前 3.6.7）
```

日志里也会记录适配过程：

```
服务端要求版本 3.7.0，当前 3.6.7，自动适配并重试
已自动适配服务端要求的版本: 3.6.7 -> 3.7.0
```

### 什么情况下仍需改代码

自动适配覆盖「服务端只是抬高了版本号」这一种情况 —— 这也是实际遇到的全部情况。
若官方大版本更新后**接口本身变了**（字段改名、新增加密、路径变更），
那就不是版本号能解决的，需要按新接口重新适配。
届时修改下面两个常量作为新的基线：

- `RunLogKotlin/app/src/main/java/com/runlog/data/AppConfig.java` → `DEFAULT_APP_EDITION`
- `RunLogKotlin/core/src/main/kotlin/com/runlog/core/AppConfig.kt` → `DefaultAppEdition`

`ConfigStore` 会把已保存的旧版本号纠正到不低于当前编译版本并回写，
所以已安装用户升级 APK 即可生效，无需手动清配置。

## 项目结构

```text
RunLogKotlin/      Android App 主工程
旧/                项目说明和历史迁移文档
```

## 文档

```text
OPEN_SOURCE_PROJECT_STRUCTURE.md  开源版完整结构说明书
RUNLOG_USER_GUIDE_README.md       软件操作说明书草稿
APK_KOTLIN_PROGRESS_AND_BUGFIX_LOG.md  重构进度与问题记录
```

主要源码：

```text
RunLogKotlin/app/src/main/java/com/runlog
```

运行时数据会写入 App 私有目录，不随源码发布。仓库不包含真实账号、Token、抓包文件、历史跑步 JSON、签名证书、APK 成品、反编译输出或远程部署配置。

## 构建

### 环境要求

| 依赖 | 要求 |
|---|---|
| JDK | **完整 JDK 17（必须包含 `jlink`）**。仅有 JRE 会在 AGP 配置阶段报 `jlink executable ... does not exist` |
| Gradle | **9.x**（本项目使用 9.2.1）。AGP 9.0.1 不接受更低版本 |
| Android SDK | 需要 `android-36` / `android-36.1` 平台 |
| Android Gradle Plugin | 9.0.1（由 `build.gradle` 声明） |

> **本仓库没有 `gradlew` wrapper**，需要自行安装 Gradle 9.x。

### 编译

```powershell
cd RunLogKotlin

# 指向本地 SDK（该文件已被 .gitignore 忽略）
"sdk.dir=D\:\\path\\to\\Android\\sdk" | Set-Content local.properties

gradle :core:runSelfTest :app:assembleDebug
```

`runSelfTest` 是纯 JVM 自测，会校验抓包解析、签名、脱敏等核心逻辑，正常输出
`RunLog Kotlin selftest passed`。

输出位置：

```text
RunLogKotlin/app/build/outputs/apk/debug/app-debug.apk
```

### 常见构建问题

| 报错 | 原因 | 解决 |
|---|---|---|
| `Could not initialize native services` | 无法写入 `~/.gradle` | 设置 `GRADLE_USER_HOME` 到可写目录 |
| `jlink executable ... does not exist` | 用的是 JRE 而非完整 JDK | 换装完整 JDK 17 |
| `AccessDeniedException: ...debug.keystore.lock` | 无法写入 `~/.android` | 加 `-Duser.home=<可写目录>` |

## 使用注意

1. **登录时校名必须输入中文**，例如「安徽新华学院」。输入拼音会报「未找到学校」——
   程序是拿输入的名字去学校列表里做字面匹配。
2. **必须关闭代理 / VPN**。若使用 Clash 等 fake-ip 模式的分流代理，域名会被解析成
   `198.18.x.x` 保留网段，导致 `ConnectException: Failed to connect to .../198.18.0.139:8000`。
3. Debug 签名的 APK 仅适合本地测试，正式分发请用自己的 release keystore 重新签名。

## 开源版变更

- 移除了旧私有访问控制、设备绑定、签名校验和远程公告依赖。
- 删除了旧私有模块源码、部署脚本、密钥、旧 APK、反编译产物和内置真实历史数据。
- 保留本地公告内容，用于提示软件免费和开源地址。
- 保留 Android 侧登录、配置导入、历史数据管理、随机/指定数据、运行进度和日志能力。

## 提交注意

提交 Issue 或 PR 时不要附带真实账号、密码、Token、deviceId、抓包文件、学校服务地址、历史跑步轨迹或 APK 签名材料。

## 感谢

感谢 [Zirconium233/yunForNewVersion](https://github.com/Zirconium233/yunForNewVersion) 提供的完整的开源学习代码。
云运动 3.4.7 版本自动跑步脚本，api 是合工大的，也可以针对接口一致的其他学校，主要解决加密问题，
提供一些小工具和教程。对于抓包教程可以访问该项目进行查看学习，有帮助的话记得点个 star。

## 免责声明

本项目仅用于学习、研究和个人测试。请遵守所在平台、学校或组织的使用规则。
