# Battery Power Overlay

面向 Root 用户的轻量级电池实时监控工具：悬浮窗 + SystemUI 状态栏功率显示。

目标设备：OnePlus Ace 6T / PLR110，Android 16 / ColorOS 16，KernelSU + LSPosed。

## 模块

- **悬浮窗**：透明文字，可拖动、可调颜色 / 字号 / 字重 / 显示项目 / 刷新频率 / 单行或自动换行，位置同步持久化。
- **状态栏**：通过 LSPosed Hook `Clock`，在时钟左侧或右侧显示实时功率，支持 X / Y 偏移、自动缩放与自适应字段。

## 架构

```
core/battery    数据模型、sysfs 读取、BatteryManager 降级、异常值校验
core/root       常驻 su Shell（marker 协议、超时、自动重建）
core/config     配置模型、编解码、迁移、跨进程契约
core/format     统一格式化（读取失败显示 --，绝不显示 0）
app             Application、悬浮窗服务、ConfigProvider、采样引擎
ui              Material 3 设置界面、HSV 调色、诊断中心
xposed          SystemUI Hook（保守、防崩、防重复注入）
```

## 构建

Gradle 8.11.1 + JDK 17 + Android SDK 36。

```bash
gradle :app:assembleRelease
```

GitHub Actions 在推送到 `main` 时自动构建并上传 APK；未配置签名 Secret 时使用 debug 签名。

## 稳定性原则

稳定 > 准确 > 持久化 > 兼容性 > UI > 扩展。

- SystemUI Hook 任何异常都会被捕获，绝不传播到 SystemUI 主线程
- 配置跨进程只走 ContentProvider，不使用 XSharedPreferences
- su 通道长期复用，禁止每次刷新创建新进程
- 读取失败显示 `--`，绝不用 `0` 伪造数据
