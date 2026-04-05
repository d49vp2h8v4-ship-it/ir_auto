# IR Auto（Minecraft 1.12.2 / Forge）

一个用于 Immersive Railroading（IR）的自动化/辅助 Mod：提供铁路地图、车站标记、信号与道岔设备、列车绑定与状态同步，以及基于时刻表/自动驾驶的数据与逻辑支持。

## 功能概览

- 铁路地图与本地扫描/缓存
- 车站标记、列车信息展示
- 信号系统与状态同步
- 道岔机与配置界面
- 列车绑定、列车位置/状态同步
- 自动驾驶/时刻表相关的数据结构与控制逻辑

## 构建与运行（开发）

先决条件：JDK 8。

本工程在编译期引用以下依赖（不建议提交到仓库）：

- ImmersiveRailroading（IR）
- TrackAPI
- UniversalModCore

把对应版本的 jar 放到 `modir/` 目录下（文件名需与 [build.gradle](file:///i:/ir_auto/MC-1.12.2-ForgeMDK-master/build.gradle#L36-L43) 一致），再刷新/导入 Gradle 工程即可。更多说明见 [modir/README.md](file:///i:/ir_auto/MC-1.12.2-ForgeMDK-master/modir/README.md)。

## 许可

仓库内包含多个许可文件（见 `LICENSE*.txt`）。

---

# IR Auto (Minecraft 1.12.2 / Forge)

An automation/utility mod for Immersive Railroading (IR). It provides a railway map, station markers, signals and turnouts, train binding & state sync, plus data structures and logic to support timetable/autopilot style control.

## Features

- Railway map + local scanning/cache
- Station markers and train display blocks/UI
- Signal system and status sync
- Turnout machine block + configuration UI
- Train binding and train position/status sync
- Autopilot/timetable related data and control logic

## Build & Run (Dev)

Prerequisite: JDK 8.

This project references the following dependencies at compile time (do not commit these jars):

- ImmersiveRailroading (IR)
- TrackAPI
- UniversalModCore

Download the correct jar versions and place them under `modir/` (filenames must match [build.gradle](file:///i:/ir_auto/MC-1.12.2-ForgeMDK-master/build.gradle#L36-L43)), then refresh/import the Gradle project. See [modir/README.md](file:///i:/ir_auto/MC-1.12.2-ForgeMDK-master/modir/README.md) for details.

## License

Multiple license files are included in this repository (see `LICENSE*.txt`).

