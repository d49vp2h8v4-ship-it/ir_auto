## 本目录说明（不提交到仓库的依赖）

`build.gradle` 里把 Immersive Railroading 相关依赖以本地 jar 的形式加入到 `provided`（仅编译期）：

- `ImmersiveRailroading-1.12.2-forge-1.10.0.jar`
- `TrackAPI-1.2.jar`
- `UniversalModCore-1.12.2-forge-1.2.1.jar`

这些 jar 通常属于第三方发布物，不建议直接提交到 GitHub。

使用方式：

1. 自行从对应 Mod 的发布页下载 jar（版本与文件名需与 `build.gradle` 保持一致）。
2. 把 jar 放到本目录下。
3. 重新导入/刷新 Gradle 工程即可。

