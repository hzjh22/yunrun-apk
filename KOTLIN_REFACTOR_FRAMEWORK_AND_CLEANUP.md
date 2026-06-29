# RunLog Kotlin 原生重构框架与冗余代码清理说明

更新时间：2026-06-20  
目标平台：Android 原生 APK  
推荐语言：Kotlin  
原项目路径：`F:\pycharm\python project\PythonProject4\yunForNewVersion-master\yunForNewVersion-master`  
建议新项目路径：`F:\CIL\yunrun\RunLogKotlin`

## 1. 重构结论

当前 Python + Kivy + Buildozer 方案已经证明接口逻辑可以跑通，但不适合作为长期 APK 方案。

主要问题：

- Buildozer 打包链复杂，依赖 Android SDK、NDK、JDK、Python 版本和网络下载。
- Python 依赖在 Android 上兼容性不稳定，尤其是 `gmssl`、`pycryptodome` 这类加密库。
- APK 体积偏大，启动速度和调试体验较差。
- Android 原生能力弱，后续做文件导入、日志、后台任务、权限、VpnService 抓包都会更麻烦。
- 后续升级维护成本高。

因此后续建议改为 Kotlin 原生 Android 项目。

重构原则：

- 不再继续扩展 Kivy UI。
- 不再继续投入 Buildozer 打包链。
- Python 项目只作为协议和算法参考。
- Kotlin 项目重新实现登录、签名、加密、配置、跑步、历史数据、日志和页面。
- Kotlin 项目同时保留两种配置获取方式：账号密码登录获取配置、手动导入抓包文件获取配置。
- 先保证功能一致，再优化 UI 和自动抓包能力。

## 2. Kotlin 项目总体架构

推荐使用 Android 原生架构：

```text
RunLogKotlin/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/runlog/
│   │   │   ├── MainActivity.kt
│   │   │   ├── ui/
│   │   │   ├── domain/
│   │   │   ├── data/
│   │   │   ├── network/
│   │   │   ├── crypto/
│   │   │   ├── task/
│   │   │   ├── log/
│   │   │   └── capture/
│   │   ├── res/
│   │   └── assets/
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

推荐模块职责：

```text
ui/
  页面和状态管理。

domain/
  业务用例，例如登录、快速跑步、打表跑步、历史提取。

data/
  配置存储、任务数据存储、日志存储。

network/
  HTTP 客户端、接口定义、请求头、签名拦截器。

crypto/
  SM2、SM4、MD5、sign 计算。

task/
  tasklist JSON 解析、随机选择、指定选择、轨迹点处理。

log/
  本地日志写入、日志读取、错误堆栈记录。

capture/
  PCAPdroid 文本导入解析、导入配置检测，后续可扩展 VpnService 自动抓包。
```

## 3. 页面框架

### 3.1 首页

首页功能：

- 显示开始跑步按钮。
- 显示当前选中的跑步数据。
- 显示跑步长度。
- 显示跑步时间。
- 显示速度或配速。
- 显示执行进度。
- 点击开始跑步前先校验配置。
- 配置不完整时跳转到我的中心或显示错误。

首页状态：

```kotlin
data class HomeUiState(
    val selectedTaskName: String?,
    val distanceText: String,
    val durationText: String,
    val speedText: String,
    val progress: Float,
    val running: Boolean,
    val message: String?
)
```

### 3.2 跑步数据页面

跑步数据页面功能：

- 显示已经提取或内置的跑步 JSON。
- 支持刷新本地任务。
- 支持从历史记录提取任务。
- 支持选择指定任务。
- 支持查看任务摘要。

任务摘要字段：

```kotlin
data class TaskSummary(
    val id: String,
    val fileName: String,
    val sourceDir: String,
    val distance: Double,
    val durationSeconds: Int,
    val speedText: String,
    val pointsCount: Int,
    val checkPointCount: Int
)
```

### 3.3 我的中心页面

我的中心页面功能：

- 显示学校 ID。
- 显示学校名称。
- 显示 `school_host`。
- 显示 app 版本。
- 显示 platform。
- 显示 token、deviceId、uuid 的脱敏值。
- 输入学校 ID、账号、密码。
- 登录并刷新 Token。
- 手动导入 PCAPdroid 抓包配置。
- 检测导入配置是否符合运行要求。
- 切换快速模式和打表模式。
- 切换随机任务和指定任务。
- 设置是否添加漂移。
- 显示日志文件和最近日志。

配置状态：

```kotlin
data class ProfileUiState(
    val schoolId: String,
    val schoolName: String,
    val schoolHost: String,
    val appEdition: String,
    val platform: String,
    val tokenMasked: String,
    val deviceIdMasked: String,
    val uuidMasked: String,
    val runMode: RunMode,
    val pickMode: PickMode,
    val driftEnabled: Boolean,
    val latestLog: String
)
```

### 3.4 抓包配置导入页面或弹窗

因为账号密码登录只能保持一台客户端在线，使用 App 内登录会导致手机客户端登录失效，所以 Kotlin 版必须支持手动导入抓包配置。

推荐入口：

```text
我的中心
-> 导入抓包配置
-> 选择 PCAPdroid 导出的 txt 文件
-> 解析配置
-> 显示解析结果
-> 检测配置
-> 用户确认保存
```

导入结果页面应显示：

- 识别到的请求 URL。
- 识别到的 host。
- 推断出的 `school_host`。
- token 脱敏值。
- deviceId 脱敏值。
- deviceName。
- uuid 脱敏值。
- appEdition。
- platform。
- 缺失字段列表。
- 检测结果。
- 风险提示，例如 host 是 `9001/api` 但跑步接口应使用 `8080`。

导入页面状态：

```kotlin
data class CaptureImportUiState(
    val selectedFileName: String?,
    val bestRequestLine: String?,
    val candidateCount: Int,
    val capturedConfig: CapturedConfig?,
    val validation: ConfigValidationResult?,
    val previewText: String,
    val canSave: Boolean,
    val message: String?
)
```

## 4. 业务流程

### 4.1 配置校验流程

运行前必须校验：

```text
school_host
publickey
cipherkey
md5key
platform
app_edition
token
device_id
device_name
uuid
```

当前确认：

```text
school_host = http://sports.aiyyd.com:8080
platform = android
app_edition = 3.6.2
```

不再强制依赖：

```text
utc
sign
```

原因：

- `utc` 运行时生成。
- `sign` 运行时根据 `platform + utc + uuid + md5key` 生成。
- 原 APK 行为是每次请求生成新的请求参数，不应依赖旧 config 中的固定 sign。

### 4.2 登录流程

Kotlin 中应实现账号密码登录：

```text
输入 schoolId/account/password
-> 根据 schoolId 选择登录接口
-> 生成 deviceId/deviceName/uuid
-> 发起登录请求
-> 获取 token
-> 写入本地配置
-> 刷新 UI
```

注意：

- `school_id == 100` 使用 `appLoginHGD`。
- `school_id == 106` 使用 `appLoginCHZU`。
- 已确认学校 `113` 应使用 `appLogin`。
- 不要继续依赖 Windows 上的账号密码文本文件路径。
- 如果用户不想挤掉手机客户端登录，应优先使用“导入抓包配置”。

登录获取配置必须保留，不能因为支持抓包导入就删除登录功能。

适用场景：

```text
首次安装 App，没有可用抓包文件
-> 使用账号密码登录获取 token/deviceId/uuid

token 已过期，且用户允许重新登录
-> 使用账号密码登录刷新配置

调试接口或确认登录协议
-> 使用账号密码登录作为标准路径
```

登录成功后要执行和抓包导入相同的配置检测流程：

```text
登录成功
-> 临时生成配置
-> 检测字段完整性
-> 请求 /run/getHomeRunInfo 验证可用性
-> 检测通过后保存
-> 保存前备份旧配置
```

### 4.2.1 手动导入抓包配置流程

该流程作为账号密码登录的并行配置获取方式，用于避免登录只能保持一台客户端的问题。

```text
用户从 PCAPdroid 导出 txt
-> 在 RunLog 中点击导入抓包配置
-> Android 文件选择器选择 txt
-> 读取文本
-> 解析 HTTP 请求行和请求头
-> 选择评分最高的候选请求
-> 提取 token/deviceId/deviceName/uuid/version/platform
-> 推断 school_host
-> 执行配置完整性检测
-> 执行接口可用性检测
-> 用户确认保存
-> 写入本地配置
```

第一版只要求导入 PCAPdroid 明文 HTTP 文本，不做内置自动抓包。

抓包文件中优先识别这些请求头：

```text
token
deviceid
devicename
version
platform
uuid
utc
sign
host
```

写入配置时的字段映射：

```text
token       -> User.token
deviceid    -> User.device_id
devicename  -> User.device_name
version     -> Yun.app_edition
platform    -> Yun.platform
uuid        -> User.uuid
host/path   -> Yun.school_host
```

不强制写入：

```text
utc
sign
```

原因：

- `utc` 每次请求运行时生成。
- `sign` 每次请求运行时计算。
- 导入旧的 `utc/sign` 可能过期。

host 处理规则：

```text
如果抓到 sports.aiyyd.com:9001/api
-> 不直接作为跑步 school_host
-> 保留为登录/通用 API host 参考
-> 跑步接口 school_host 优先修正为 http://sports.aiyyd.com:8080
```

如果抓到：

```text
sports.aiyyd.com:8080
```

则可以直接作为跑步接口 host。

### 4.2.2 导入配置检测规则

导入后必须先检测，不允许直接覆盖可用配置。

基础字段检测：

```text
token        必须存在，长度不能过短
device_id    必须存在，长度不能过短
device_name  必须存在
uuid         必须存在，格式应接近 UUID
platform     必须存在，建议为 android
app_edition  必须存在，建议为 3.6.2
school_host  必须存在，跑步接口建议为 http://sports.aiyyd.com:8080
```

接口可用性检测：

```text
使用导入配置临时请求 /run/getHomeRunInfo
-> HTTP 200 且业务响应可解析：检测通过
-> HTTP 401/403：token 或请求头无效
-> 连接失败：host 或网络异常
-> 解密失败：cipherkey/publickey/sign 逻辑异常
```

检测时不要立刻执行跑步，不调用：

```text
/run/start
/run/finish
/run/splitPointCheating
```

保存策略：

```text
检测通过：允许保存为当前配置
基础字段通过但接口检测失败：允许保存为草稿，不允许开始跑步
基础字段缺失：不允许保存为当前配置
```

配置保存前必须备份旧配置：

```text
config_backup_YYYYMMDD_HHMMSS.json
```

### 4.2.3 两种配置获取方式的关系

Kotlin 版必须同时支持：

```text
方式一：账号密码登录获取配置
方式二：手动导入 PCAPdroid 抓包文件获取配置
```

两种方式进入同一个配置检测和保存流程：

```text
账号密码登录
       │
       ├──> 生成 CandidateConfig
       │
抓包文件导入
       │
       └──> 生成 CandidateConfig

CandidateConfig
-> 基础字段检测
-> 接口可用性检测
-> 用户确认
-> 备份旧配置
-> 保存为当前配置
```

优先级建议：

```text
有可用抓包文件：优先导入抓包配置，避免手机客户端掉线。
没有抓包文件：使用账号密码登录。
token 失效：优先重新导入抓包配置；如果没有抓包文件，再账号密码登录。
调试阶段：两种方式都保留，方便对照定位问题。
```

UI 上不要把两种方式混在一个按钮里，应明确区分：

```text
按钮 1：登录并刷新 Token
按钮 2：导入抓包配置
按钮 3：检测当前配置
```

检测当前配置按钮用于不修改配置的情况下验证现有配置是否还能使用。

### 4.3 快速模式流程

```text
点击开始跑步
-> 校验配置
-> getHomeRunInfo
-> start
-> finish_quick
-> finish
-> 显示结果
-> 写入日志
```

用途：

- 验证配置有效。
- 验证 token 是否有效。
- 验证跑步接口是否可访问。

### 4.4 打表模式流程

```text
点击开始跑步
-> 校验配置
-> 选择 tasklist JSON
   -> 随机模式：从当前任务目录随机选择
   -> 指定模式：使用用户选中的 JSON
-> getHomeRunInfo
-> start
-> 分段上报 splitPointCheating
-> finish
-> 显示结果
-> 写入日志
```

注意：

- 打表模式下跑步距离、时间、速度主要来自 `tasklist_*.json`。
- `config.ini` 中的手动跑步参数不应覆盖 tasklist 数据。
- 上报过程中要在后台协程中执行，不能阻塞 UI 线程。

### 4.5 历史数据提取流程

```text
读取配置
-> 请求历史学期列表
-> 请求历史跑步列表
-> 请求跑步详情
-> 解密
-> gzip 解压
-> 解析 tasklist
-> 保存到本地任务目录
-> 刷新跑步数据页面
```

注意：

- 当前 Python 项目保留了历史提取逻辑，但接口返回空响应的问题需要继续验证。
- Kotlin 重构时不要盲目照搬旧接口路径，要以真实 APK 或抓包结果为准。

## 5. 网络层设计

推荐依赖：

```kotlin
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
```

网络层结构：

```text
network/
├── ApiClient.kt
├── YunApi.kt
├── HeaderInterceptor.kt
├── SignInterceptor.kt
├── ApiResult.kt
└── NetworkError.kt
```

请求头处理：

- token。
- deviceId。
- deviceName。
- platform。
- appVersion。
- schoolId。
- utc。
- uuid。
- sign。

注意：

- 跑步接口使用 `http://sports.aiyyd.com:8080`。
- 登录或学校列表相关接口可能使用 `http://sports.aiyyd.com:9001/api`。
- Android 9 以后默认限制明文 HTTP，必须配置 cleartext。

`AndroidManifest.xml` 中需要：

```xml
<application
    android:usesCleartextTraffic="true"
    android:networkSecurityConfig="@xml/network_security_config">
</application>
```

`res/xml/network_security_config.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">sports.aiyyd.com</domain>
    </domain-config>
</network-security-config>
```

权限：

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## 6. 加密与签名迁移

Python 中涉及：

- SM2。
- SM4。
- gzip。
- MD5 sign。
- JSON 加解密。

Kotlin 中建议建立独立模块：

```text
crypto/
├── Sm2Crypto.kt
├── Sm4Crypto.kt
├── Md5Sign.kt
├── GzipCodec.kt
└── CryptoCompatTest.kt
```

注意事项：

- 不要直接使用 Android 系统自带的老 BouncyCastle Provider。
- 优先使用 BouncyCastle lightweight API 或经过验证的国密库。
- 必须确认 SM2 密文格式，例如 `C1C2C3` 或 `C1C3C2`。
- 必须确认 SM4 模式、padding、key、iv 处理方式。
- Kotlin 实现后要用 Python 旧代码的样例数据做兼容测试。

推荐先建立兼容测试：

```text
Python 输入明文 -> 输出密文
Kotlin 输入同样明文 -> 输出应一致或服务端可接受

Python 输入服务端密文 -> 解密明文
Kotlin 输入同样密文 -> 解密明文一致
```

## 7. 数据存储设计

### 7.1 配置存储

不建议把真实 token 固定写入 assets。

推荐：

- 普通配置：DataStore。
- 敏感配置：EncryptedSharedPreferences 或 Android Keystore 加密后存储。
- 首次启动可从 assets 中读取 `config.sample.ini` 或默认配置。

配置对象：

```kotlin
data class AppConfig(
    val schoolId: String,
    val schoolName: String,
    val schoolHost: String,
    val appEdition: String,
    val platform: String,
    val publicKey: String,
    val cipherKey: String,
    val md5Key: String,
    val token: String,
    val deviceId: String,
    val deviceName: String,
    val uuid: String
)
```

### 7.2 任务数据存储

内置任务可放在：

```text
app/src/main/assets/tasks_else/
app/src/main/assets/tasks_fch/
app/src/main/assets/tasks_txl/
app/src/main/assets/tasks_xc/
```

用户提取或导入的任务保存到：

```text
context.filesDir/tasks_else/
```

注意：

- assets 只读。
- App 运行时新增数据必须写到私有目录。
- UI 显示时要合并 assets 任务和私有目录任务。

### 7.3 日志存储

日志目录：

```text
context.filesDir/logs/
```

日志内容：

- 启动记录。
- 配置校验结果。
- 登录结果。
- 跑步模式。
- 任务 JSON 选择结果。
- 接口请求失败原因。
- 异常堆栈。

日志页面不要显示完整 token。

## 8. PCAPdroid 文本导入方案

第一阶段不做内置自动抓包，先做文本导入。该功能和账号密码登录同时保留。

推荐流程：

```text
PCAPdroid 导出 txt
-> 用户在 APK 中选择文件
-> 解析请求头和响应
-> 提取 token/deviceId/deviceName/uuid/appEdition/platform
-> 检测字段完整性
-> 临时请求 getHomeRunInfo 检测配置是否可用
-> 用户确认后写入本地配置
-> 刷新我的中心页面
```

Kotlin 模块：

```text
capture/
├── PcapTextParser.kt
├── CapturedConfig.kt
├── CaptureCandidate.kt
├── CaptureConfigValidator.kt
└── ImportCaptureUseCase.kt
```

后续如需自动抓包，再评估：

```text
VpnService
-> 本地 VPN 授权
-> 过滤 sports.aiyyd.com
-> 提取明文 HTTP
```

注意：

- VpnService 需要用户授权。
- Android 同一时间只能运行一个 VPN。
- 如果目标接口改成 HTTPS，不能直接读取明文请求头。
- Kivy/Buildozer 做 VpnService 成本高，Kotlin 原生更适合。

抓包导入验收要求：

- 可以选择 PCAPdroid 导出的 txt 文件。
- 可以解析出至少一个候选 HTTP 请求。
- 可以显示评分最高的候选请求。
- 可以提取 token、deviceId、deviceName、uuid、version、platform。
- 可以识别 host，并把跑步接口 host 修正为 `http://sports.aiyyd.com:8080`。
- 可以检测缺失字段。
- 可以临时请求 `/run/getHomeRunInfo` 检测配置是否可用。
- 检测失败时不覆盖当前可用配置。
- 检测通过后保存前自动备份旧配置。

## 9. 冗余代码清理方案

### 9.1 不再迁移到 Kotlin 的内容

以下内容不建议迁移到 Kotlin 项目：

```text
buildozer.spec
mobile_app.py
mobile_services.py
selftest.py
requirements.txt
decrypt_task_tutorial.ipynb
auto_utf8.py
auto_full.py
12.py
cs.py
__pycache__/
.venv/
.venv-linux/
logs/
auto_out/
session.log
```

原因：

- 这些主要服务于 Python/Kivy/Buildozer 方案。
- Kotlin 原生项目不需要 Python 虚拟环境、Buildozer、Kivy UI。
- 日志和缓存属于运行产物，不应进入新项目。

### 9.2 需要保留作为参考的内容

以下内容需要保留，直到 Kotlin 功能完全验证：

```text
main.py
history.py
auto_history_fetch.py
tools/Login.py
tools/getUrl_Id.py
tools/pcap_config.py
config.ini
map.json
tasks_else/
tasks_fch/
tasks_txl/
tasks_xc/
README.md
history.md
proxy.md
questions.md
APK_PROJECT_PLAN.md
APK_DEPLOY_FRAMEWORK_FUNCTION_SPEC.md
```

原因：

- `main.py` 是当前最完整的协议参考。
- `history.py` 和 `auto_history_fetch.py` 是历史数据提取参考。
- `tools/Login.py` 是登录接口参考。
- `tools/pcap_config.py` 是抓包文本解析参考。
- `tasks_*` 是打表模式测试数据。
- 文档用于保留决策和问题记录。

### 9.3 清理方式

不要直接在原 Python 项目里大规模删除。

推荐流程：

```text
1. 新建 Kotlin 项目 RunLogKotlin。
2. 只复制必要 assets 和协议参考文档。
3. Python 项目保持原样，作为对照实现。
4. Kotlin 功能通过后，再归档 Python 项目。
5. 最后删除构建缓存、虚拟环境、日志和临时文件。
```

建议归档结构：

```text
F:\CIL\yunrun\
├── RunLogKotlin\
├── python_reference\
├── docs\
└── samples\
```

其中：

```text
RunLogKotlin/
  新 Kotlin Android 项目。

python_reference/
  精简后的 Python 协议参考，不再作为主项目维护。

docs/
  重构说明、部署说明、协议说明。

samples/
  APK 样本、抓包样本、任务 JSON 样本。
```

## 10. Kotlin 重构阶段计划

### 阶段 1：建立原生项目骨架

目标：

- 创建 Android Studio Kotlin 项目。
- 配置包名。
- 配置网络权限。
- 配置 cleartext HTTP。
- 建立三页面导航。
- 建立配置存储和日志存储。

验收：

- APK 能安装。
- 三个页面能切换。
- 日志能写入并显示。

### 阶段 2：迁移配置和登录

目标：

- 实现配置模型。
- 实现账号密码登录。
- 保存 token、deviceId、uuid。
- 我的中心页面能刷新配置。

验收：

- 使用学校 ID、账号、密码能登录成功。
- 登录后配置持久化。
- 关闭重开 App 后配置仍存在。

### 阶段 3：迁移签名和加密

目标：

- 实现 MD5 sign。
- 实现 SM2。
- 实现 SM4。
- 实现 gzip 解压。
- 建立 Python/Kotlin 兼容测试样例。

验收：

- Kotlin 请求能被服务端接受。
- 跑步接口能返回正常响应。

### 阶段 4：迁移快速模式

目标：

- 实现 `getHomeRunInfo`。
- 实现 `start`。
- 实现 `finish_quick`。
- 实现 `finish`。

验收：

- 快速模式可以完成一次跑步流程。
- 日志记录完整。

### 阶段 5：迁移打表模式

目标：

- 解析 `tasklist_*.json`。
- 支持随机任务。
- 支持指定任务。
- 分段上报轨迹点。
- 支持漂移开关。

验收：

- 能显示任务列表。
- 能选择任务。
- 能完成打表模式流程。

### 阶段 6：迁移历史数据和抓包文本导入

目标：

- 实现历史数据提取。
- 实现 PCAPdroid 文本导入。
- 保存提取出的 tasklist。

验收：

- 跑步数据页面能显示新提取数据。
- 导入抓包文本后配置可刷新。

### 阶段 7：清理 Python 旧项目

目标：

- Kotlin 功能完整后，精简 Python 项目。
- 删除 Buildozer/Kivy 相关代码。
- 保留协议参考和样本数据。

验收：

- Kotlin 项目成为主项目。
- Python 项目只作为参考归档。
- 文档和样本清晰可追溯。

## 11. 重要注意事项

### 11.1 明文 HTTP

当前接口是 HTTP，不是 HTTPS。

Android 9 以后默认限制明文 HTTP，因此必须配置：

```xml
android:usesCleartextTraffic="true"
```

以及 `network_security_config.xml`。

### 11.2 不要硬编码真实账号和 token

禁止把真实账号、密码、token、deviceId 固定写入源码或 assets。

应使用：

- 用户输入。
- 本地私有存储。
- 脱敏显示。
- 日志过滤。

### 11.3 加密兼容性是最大风险

Kotlin 重构最大风险不是 UI，而是：

- SM2 格式。
- SM4 模式。
- sign 生成。
- 请求头顺序和字段。
- gzip 解压。

必须先建立 Python/Kotlin 对照测试。

### 11.4 接口 host 要分清

已确认：

```text
跑步接口：sports.aiyyd.com:8080
部分登录/通用接口：sports.aiyyd.com:9001/api
```

不能把所有接口都放到一个 host。

### 11.5 日志必须从第一版就加入

Android 真机调试不如桌面方便。

必须保留：

- 文件日志。
- UI 日志页面。
- `adb logcat` 输出。
- 异常堆栈。

### 11.6 先做功能闭环，再做自动抓包

自动抓包涉及 VpnService 和网络解析，复杂度高。

推荐顺序：

```text
账号密码登录
-> 手动导入 PCAPdroid 文本
-> 后续评估 VpnService 自动抓包
```

## 12. 推荐技术栈

推荐：

```text
Kotlin
Jetpack Compose
Navigation Compose
ViewModel
Kotlin Coroutines
OkHttp
kotlinx.serialization
DataStore
EncryptedSharedPreferences 或 Android Keystore
WorkManager 可选
```

加密库需要实测：

```text
BouncyCastle lightweight API
或其他经过 Android 验证的国密库
```

不建议：

```text
Kivy
Buildozer
Python-for-Android
mitmproxy 内置到 APK
把真实 config.ini 直接打包进 APK
```

## 13. 第一版 Kotlin APK 最小可交付范围

第一版不追求所有功能一次完成。

最小范围：

```text
1. 三页面 UI。
2. 本地配置存储。
3. 账号密码登录刷新 token。
4. 手动导入 PCAPdroid 抓包配置。
5. 检测当前配置是否可用。
6. 配置完整性校验。
7. 跑步数据 JSON 列表显示。
8. 快速模式跑通。
9. 日志页面。
```

第二版再加入：

```text
1. 打表模式。
2. 随机/指定任务。
3. 历史数据提取。
4. 配置导入导出备份增强。
```

第三版再评估：

```text
1. VpnService 自动抓包。
2. 多账号。
3. 后台任务。
4. 轨迹预览。
```
