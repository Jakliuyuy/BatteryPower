# BatteryPower · 电池功率悬浮窗

在**任意 App 之上**和**系统状态栏内**实时显示电池功率（W）、电流（mA）、电压（V）、温度（℃）、电量（%）。

针对 **一加 Ace 6T（PLR110，Android 16 / ColorOS 16，KernelSU + LSPosed）** 开发，代码里做了通用降级，其他机型也能用。

---

## 两种显示模式

### 1. 悬浮窗（无需 Xposed，root 可用即可）
- 全透明无背景，拖动到任意位置，位置自动记忆
- 显示项任意组合：功率 / 电流 / 电压 / 温度 / 电量
- HSV 无极调色板 + 8 组预设色，字号 8~28sp
- 刷新频率 0.5s / 1s / 2s，单位可开关
- 前台服务保活，常驻通知可一键关闭

### 2. 状态栏内嵌（需要 LSPosed）
- 直接注入 SystemUI 状态栏，风格对齐 Scene 迷你监视器
- **elevation 100**，通知图标再多也盖不住它
- 时钟左/右侧 + 水平 ±2000px / 垂直 ±1000px 微调（滑块 + 10px 步进）
- 改配置 **0.5 秒内生效**，无需重启 SystemUI
- App 内置「重启 SystemUI」按钮（root `pkill`）

---

## 安装

1. 下载 `BatteryPower-release.apk` 安装
2. **悬浮窗模式**：打开 App → 点「授权悬浮窗」→ 打开「启用悬浮窗」开关
3. **状态栏模式**：
   - 在 LSPosed 管理器里勾选本模块
   - 在作用域里选中 **SystemUI**
   - 回 App 点「重启 SystemUI」
   - 打开「启用状态栏内嵌显示」开关
4. 若数值恒为 0，用 App 内的**电池节点诊断**看具体原因

> APK 每次升级后，SystemUI 进程里跑的还是旧模块代码，**必须重启 SystemUI** 才会生效。

---

## 数据是怎么读的

三条通道，按优先级自动降级：

| 优先级 | 通道 | 说明 |
|---|---|---|
| 1 | `sysfs` 直读 | SystemUI（uid 1000）可直接读，最快最准 |
| 2 | `sysfs` + root | 主 App 走**常驻 su 会话**读取 |
| 3 | `BatteryManager` | 无 root 兜底，电流可能恒 0 |

### 单位处理（最容易踩的坑）

实测 PLR110（内核 6.12）的节点单位：

```
power_now   = 3460000  ← μW，直接就是功率，优先用它
current_now = 865      ← 是 mA！不是文档约定的 μA
voltage_now = 4005000  ← μV
temp        = 386      ← 0.1℃
capacity    = 66       ← %
```

- **优先读 `power_now`**，天然规避电流单位歧义
- `current_now` 做**自动判定**：绝对值 > 100000 视作 μA，否则视作 mA
- 充电显示**正**功率、放电显示**负**功率（按 `status` 定符号，不管节点值本身正负）

### 为什么必须用常驻 su 会话

每秒新起一个 `su` 进程会被 KernelSU 限流，实测 2s 超时根本不够。
本实现只启动一次 `su`，之后靠 `stdin` 写命令、按 `KOMARI_DONE_*` marker 从 `stdout` 切分结果。

---

## 跨进程配置通道

SystemUI 进程要拿到主 App 里改的配置，这里**不用 XSharedPreferences**（Android 11+ 起主 App 的 SP 是 0600，SystemUI 读不到，表现为"改了没反应，全是默认值"）。

改用三层：

```
主 App 进程                          SystemUI 进程
SharedPreferences
      ↓
ConfigProvider (exported)  ──query──▶  ① 内存缓存
                                       ② SystemUI 自己的 DE SharedPreferences
                                       ③ 默认值
```

- Provider 轮询间隔 500ms，与显示刷新频率解耦 → 改设置 0.5s 内生效
- 查询失败做 10s 节流，避免拖慢 SystemUI
- 主 App 被杀、SystemUI 重启，配置都不丢

---

## 状态栏注入细节

```
hook Clock.onAttachedToWindow
  时钟类名候选：com.oplus.systemui.statusbar.policy.Clock   (ColorOS)
              → com.android.systemui.statusbar.policy.Clock (AOSP)
  挂载点：向上找最外层 FrameLayout（不是时钟父容器，否则会被通知挤掉）
  定位：getLocationOnScreen 换算屏幕坐标（clock 与 root 坐标系不同）
  层级：elevation = translationZ = 100
```

---

## 自动构建

推送到 `main` 分支即触发 GitHub Actions：

- JDK 17 + Gradle 8.11.1 + AGP 8.9.2
- 同时产出 debug / release 两个包（applicationId 不同，可共存）
- 产物上传到：Actions Artifact、`apk` 分支、Release (`apk-latest` 标签)
- 每次构建把完整 Gradle 日志推到 `build-logs` 分支，方便排查

想用正式签名：在仓库 Settings → Secrets 里配置

| Secret | 说明 |
|---|---|
| `KEYSTORE_BASE64` | keystore 文件 base64 编码 |
| `KEYSTORE_PASS` | keystore 密码 |
| `KEY_ALIAS` | 密钥别名 |

不配置也行，会自动生成自签密钥，不影响安装使用。

本地构建：

```bash
export ANDROID_HOME=$HOME/Android/Sdk
gradle wrapper --gradle-version 8.11.1 --distribution-type bin
./gradlew assembleDebug
```

> 仓库不含 `gradle-wrapper.jar`（二进制文件），由 CI 现场生成。

---

## 目录结构

```
app/src/main/java/com/jakliuyuy/batterypower/
├── MainActivity.kt              设置界面（悬浮窗 / 状态栏 / 诊断）
├── model/
│   ├── BatterySnapshot.kt       采样结果 + 可显示字段枚举
│   └── Config.kt                配置模型，主 App 与 SystemUI 共用
├── data/ConfigRepository.kt     配置读写 + 渲染成显示文字
├── provider/ConfigProvider.kt   跨进程配置通道
├── battery/
│   ├── RootHelper.kt            常驻 su shell 会话
│   ├── BatteryProbe.kt          sysfs / BatteryManager 三通道读取
│   └── BatteryReader.kt         定时采样调度
├── overlay/
│   ├── OverlayService.kt        前台服务 + 悬浮窗生命周期
│   └── BatteryOverlayView.kt    全透明可拖动 TextView
├── xposed/
│   ├── HookEntry.kt             LSPosed 入口（assets/xposed_init 声明）
│   ├── SystemUIHook.kt          状态栏注入
│   └── ConfigBridge.kt          SystemUI 侧配置读取
└── ui/HsvColorPickerView.kt     自绘 HSV 调色板（无第三方依赖）
```

---

## 已知限制

- 状态栏模式依赖 LSPosed，纯 Magisk 无 Xposed 环境只能用悬浮窗模式
- 部分 ROM 的 SystemUI 类名是定制的，若候选类名都匹配不上，模块静默不生效（看 LSPosed 日志）
- 无 root 时 `BatteryManager` 在部分机型上电流恒为 0，App 会明确提示而非显示 0.00W

## 版本

`v1.0` — 悬浮窗 + 状态栏双模式，双进程配置同步，电池节点诊断
