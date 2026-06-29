# RunLog 开源版项目结构说明书

更新时间：2026-06-29

开源地址：

```text
https://github.com/hzjh22/yunrun-apk
```

## 1. 开源版定位

RunLog 开源版是 Android 原生跑步数据管理与运行测试工具，主要用于学习、研究和个人测试。

开源版已经移除旧私有访问控制、设备绑定、签名校验、远程公告和所有私有部署内容。App 公告页固定显示本地开源免费声明：

```text
本软件完全免费，如果你付费了说明你被骗了。
感觉软件好用的话请点击一个 Star，谢谢。
开源地址：https://github.com/hzjh22/yunrun-apk
```

## 2. 根目录结构

```text
yunrun/
├── README.md
├── RUNLOG_USER_GUIDE_README.md
├── OPEN_SOURCE_PROJECT_STRUCTURE.md
├── APK_KOTLIN_PROGRESS_AND_BUGFIX_LOG.md
├── KOTLIN_REFACTOR_FRAMEWORK_AND_CLEANUP.md
├── RunLogKotlin/
│   ├── app/
│   ├── core/
│   ├── tools/
│   ├── build.gradle
│   ├── gradle.properties
│   └── settings.gradle
└── 旧/
    └── APK_DEPLOY_FRAMEWORK_FUNCTION_SPEC.md
```

说明：

- `RunLogKotlin/` 是 Android 主工程。
- `README.md` 是 GitHub 首页说明。
- `RUNLOG_USER_GUIDE_README.md` 是软件使用说明书草稿，可继续补充截图和发行说明。
- `OPEN_SOURCE_PROJECT_STRUCTURE.md` 是开源结构说明书。
- `APK_KOTLIN_PROGRESS_AND_BUGFIX_LOG.md` 是已净化的迁移进度记录。
- `旧/` 只保留不含私有服务内容的历史结构说明。

## 3. Android 工程结构

```text
RunLogKotlin/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets_runtime/
│       ├── java/com/runlog/
│       └── res/
├── core/
│   ├── build.gradle
│   └── src/
├── tools/
│   └── SelfTest.java
├── build.gradle
├── gradle.properties
└── settings.gradle
```

## 4. App 源码模块

源码目录：

```text
RunLogKotlin/app/src/main/java/com/runlog
```

模块说明：

```text
MainActivity.java
  App 主界面和页面调度。负责首页、数据页、我的中心、公告页、运行进度、配置检测和用户操作入口。

capture/
  手动导入 PCAPdroid 文本后的配置解析逻辑。

crypto/
  SM4、MD5 请求签名和接口加解密辅助逻辑。

data/
  AppConfig、配置读写、配置校验、候选配置模型和敏感字段脱敏。

log/
  App 私有目录下的日志文件写入、清理和最近日志读取。

network/
  登录刷新 Token、在线历史数据提取、跑步提交、基础 API 客户端、本地公告模型。

task/
  SQLite 历史数据存储、重复数据合并、任务摘要、随机/指定选择、出现次数和使用次数统计。
```

## 5. 资源目录

```text
RunLogKotlin/app/src/main/res/
├── mipmap-*/             App 图标
├── values/               字符串和样式
└── xml/
    └── network_security_config.xml
```

当前网络安全配置只保留 `sports.aiyyd.com` 明文访问例外，用于业务接口兼容。

```text
RunLogKotlin/app/src/main/assets_runtime/
```

`assets_runtime` 保留空占位，不内置真实历史跑步 JSON。

## 6. 数据存储

App 运行数据保存在 Android App 私有目录：

- 配置：`SharedPreferences`
- 日志：App 私有文件目录
- 历史跑步数据：SQLite
- 导入后的任务 JSON：App 私有目录或 SQLite 管理

仓库不保存：

- 真实账号、密码、Token、deviceId、uuid。
- 抓包文件、HAR、PCAP、PCAPNG。
- 真实历史跑步轨迹 JSON。
- APK、AAB、签名证书、keystore、密钥。
- 反编译输出、构建缓存、日志和本地数据库。

## 7. 核心流程

### 7.1 配置获取

支持两种方式：

- 账号密码登录刷新 Token。
- 手动导入抓包文本提取请求头配置。

配置保存前会做完整性检测，不完整时保存为草稿，避免直接运行。

### 7.2 历史数据获取

支持两种方式：

- 手动导入历史 JSON/TXT。
- 在线提取历史跑步数据。

导入时会计算任务指纹，重复数据合并为一条记录，不重复保存。

### 7.3 数据选择

支持两种模式：

- 随机模式：从可用历史数据中随机选取。
- 指定模式：用户在数据页点击“指定使用”。

数据页支持“换一组”，随机展示 5 条数据。

### 7.4 跑步运行

点击开始跑步后：

```text
配置检测 -> 选择任务数据 -> 读取任务 JSON -> 执行 Android 原生运行逻辑 -> 实时显示进度 -> 写入日志
```

停止按钮会请求中断当前运行流程。

## 8. 构建方式

进入工程：

```powershell
cd F:\CIL\yunrun\RunLogKotlin
```

执行：

```powershell
gradle :app:assembleDebug
```

输出：

```text
RunLogKotlin/app/build/outputs/apk/debug/app-debug.apk
```

## 9. 开源清理状态

已完成：

- 删除旧私有访问控制相关源码和工具。
- 删除旧远程服务源码、部署脚本和管理脚本。
- 删除旧 APK、密钥、签名材料、反编译输出和压缩包。
- 删除内置真实历史任务 JSON。
- 删除本地登录/历史探针工具。
- 更新 `.gitignore`，避免再次提交本地运行产物。

推送前建议继续执行：

```powershell
rg -n "<替换为需要检查的敏感关键词>" .
git status --short
```
