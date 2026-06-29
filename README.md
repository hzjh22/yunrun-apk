# RunLog
本项目完全使用AI Coding完成
RunLog 是一个 Android 原生跑步数据管理与运行测试工具。

本软件完全免费，如果你付费了说明你被骗了。感觉软件好用的话请点击一个 Star，谢谢。

开源地址：https://github.com/hzjh22/yunrun-apk

## 功能

- 首页显示开始跑步、停止跑步、当前数据、长度、时间、配速和运行进度。
- 数据页支持导入历史 JSON/TXT、在线提取历史数据、重复数据合并、随机显示一组数据、指定某条数据运行。
- 我的中心支持配置检测、账号登录刷新 Token、导入抓包配置、切换随机/指定模式、清除本地历史数据、查看运行日志。
- 公告页使用本地开源声明，不依赖远程公告接口。

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

进入 Android 工程目录后执行：

```powershell
cd RunLogKotlin
gradle :app:assembleDebug
```

输出位置：

```text
RunLogKotlin/app/build/outputs/apk/debug/app-debug.apk
```

## 开源版变更

- 移除了旧私有访问控制、设备绑定、签名校验和远程公告依赖。
- 删除了旧私有模块源码、部署脚本、密钥、旧 APK、反编译产物和内置真实历史数据。
- 保留本地公告内容，用于提示软件免费和开源地址。
- 保留 Android 侧登录、配置导入、历史数据管理、随机/指定数据、运行进度和日志能力。

## 提交注意

提交 Issue 或 PR 时不要附带真实账号、密码、Token、deviceId、抓包文件、学校服务地址、历史跑步轨迹或 APK 签名材料。
## 感谢

感谢[Zirconium233/yunForNewVersion: 云运动3.4.7版本自动跑步脚本，api是合工大的，也可以针对接口一致的其他学校，主要解决加密问题，提供一些小工具和教程。](https://github.com/Zirconium233/yunForNewVersion)提供的完整的开源学习代码。对于抓包教程可以访问改项目进行查看学习，有帮助的话记得点个start
## 免责声明
本项目仅用于学习、研究和个人测试。请遵守所在平台、学校或组织的使用规则。
