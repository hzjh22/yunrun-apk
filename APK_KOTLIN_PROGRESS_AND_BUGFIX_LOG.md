# APK Kotlin/Android 重构进度与问题记录

更新时间：2026-06-29

## 当前状态

主工程：

```text
F:\CIL\yunrun\RunLogKotlin
```

当前 debug APK：

```text
F:\CIL\yunrun\RunLogKotlin\app\build\outputs\apk\debug\app-debug.apk
```

当前项目已迁移到 Android 原生工程，运行时不执行 Python `main.py`。页面、配置、登录、抓包导入、历史数据导入、在线历史数据提取、日志记录、随机/指定数据选择、SQLite 历史数据存储、重复数据合并和运行进度展示已经接入。

## 2026-06-29 开源化清理

为了发布到 GitHub，已完成开源版清理：

- 移除旧私有访问控制、设备绑定、签名校验和远程公告依赖。
- 删除旧私有模块源码、部署脚本、管理脚本和私有服务文档。
- 删除私钥、公钥、签名材料、旧 APK、压缩包、反编译输出、本地验证产物。
- 删除仓库内置的真实历史跑步 JSON，避免把个人轨迹数据打进 APK 或提交到 GitHub。
- `network_security_config.xml` 仅保留 `sports.aiyyd.com` 的明文访问例外。
- App 公告页改为本地开源免费声明，不联网请求公告。

## 2026-06-29 getHomeRunInfo 兼容修复

问题现象：

```text
Run failed:
IllegalStateException: getHomeRunInfo has no cralist
```

原因：

- `/run/getHomeRunInfo` 返回 `code=200` 和 `msg=操作成功`，但 `data` 中只有 `isAvoid`，没有旧逻辑要求的 `cralist`。
- 旧代码把 `data.cralist` 当成必需字段，因此成功响应也会被本地误判为失败。

修复：

- `RunClient` 先解析当前选中的历史 JSON，再请求 `getHomeRunInfo`。
- 如果响应包含 `cralist`，继续使用接口返回的跑区参数。
- 如果响应不包含 `cralist`，不再直接失败，改为从历史 JSON 和当前配置中提取 `raId`、`raType`、`raRunArea`、`schoolId`、`recodeCadence` 等字段。
- 不写死学校 ID、学校名称、跑区或校区内容。
- `run/start`、`run/finish` 和分段提交只携带已提取到的可用字段，避免提交空字符串伪配置。
- 抓包 TXT 导入新增 `schoolId`、`schoolName` 提取，并把 `:9001/api` 同域推导为 `:8080`，不再固定到某个域名。

自测：

- `:app:assembleDebug` 构建通过。
- 固定学校、私有服务器和授权相关敏感关键词扫描无命中。

## 页面与功能

首页：

- 显示开始跑步、停止跑步、当前选中跑步数据、长度、时间、配速和运行进度。
- 点击开始跑步后执行 Android 原生运行逻辑，不依赖 Python。
- 开始前仍会检测配置完整性和历史数据可用性。

数据页：

- 支持导入历史 JSON/TXT。
- 支持在线提取历史数据。
- 支持重复数据合并。
- 支持随机模式和指定模式。
- 支持“换一组”随机展示 5 条数据。
- 卡片显示出现次数和使用次数。

我的中心：

- 显示开源免费状态。
- 显示 deviceName、token、deviceId、数据选择模式和草稿状态。
- 不展示 `schoolHost`。
- 支持检测配置、刷新 Token、导入配置、公告、随机/指定模式切换、漂移开关、清除历史数据、日志查看。

公告页：

- 固定显示：

```text
本软件完全免费，如果你付费了说明你被骗了。
感觉软件好用的话请点击一个 Star，谢谢。
开源地址：https://github.com/hzjh22/yunrun-apk
```

## 数据层

- 历史跑步数据已迁移到 SQLite。
- 数据导入时计算任务指纹，重复数据合并为一条记录。
- 重复导入不会继续保存同一数据。
- 任务记录保留出现次数和使用次数，便于在数据页展示。

## 构建要求

构建命令：

```powershell
cd F:\CIL\yunrun\RunLogKotlin
gradle :app:assembleDebug
```

如果本机没有全局 `gradle`，使用 Android Studio/IDEA 配置的 Gradle 执行同一任务。

## 后续注意

- 提交前继续运行敏感信息扫描，避免账号、Token、抓包、私钥、APK、真实轨迹数据进入仓库。
- 发布 APK 时使用自己的签名文件，签名文件不要提交。
- Issue 和 PR 不要附带真实抓包文件或完整日志。
