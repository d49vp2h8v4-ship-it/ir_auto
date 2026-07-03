# IR Auto — Immersive Railroading 自动控制附属模组

IR Auto 是 Immersive Railroading 的功能增强模组，提供自动驾驶、信号系统、铁路地图、时刻表管理等功能。

## 许可证

本项目使用 **GNU Lesser General Public License v2.1 (LGPL 2.1)**。

## 免责声明

- **IR Auto 并非 Immersive Railroading 官方项目**，与 IR 开发团队无任何关联。
- 本项目是 IR 的附属模组（addon），运行时必须安装 Immersive Railroading。
- IR Auto 不包含、不分发、不修改 Immersive Railroading 的任何代码或资源。
- 本项目中所有涉及 Immersive Railroading 内部 API 的反射调用均为运行时动态调用，不存在编译期代码复制或静态链接。
- Immersive Railroading 及其相关资产的版权归其原作者所有。

## 运行环境

| 组件 | 版本 |
|------|------|
| Minecraft | 1.12.2 |
| Forge | 14.23.5.2838 |
| JDK | 8 |
| Immersive Railroading | 1.12.2-forge-1.10.0 |
| TrackAPI | 1.2 |
| UniversalModCore | 1.12.2-forge-1.2.1 |

## 开发环境搭建

```bash
# 1. 克隆仓库
git clone https://github.com/d49vp2h8v4-ship-it/ir_auto.git
cd ir_auto

# 2. 创建 modir 目录，放入 IR 依赖
mkdir modir
# 下载以下 3 个 jar 放到 modir/ 目录：
#   ImmersiveRailroading-1.12.2-forge-1.10.0.jar
#   TrackAPI-1.2.jar
#   UniversalModCore-1.12.2-forge-1.2.1.jar

# 3. 初始化 Forge 开发环境
./gradlew setupDecompWorkspace

# 4. 启动测试客户端
./gradlew runClient
```

## 项目结构

```
src/main/java/com/chuanshuoi9/
├── block/       方块（信号机、道岔机、站牌等）
├── tile/        方块实体数据
├── item/        物品（连接器、地图、时刻表等）
├── signal/      信号系统（三状态信号机、列车检测）
├── train/       自动驾驶、时刻表管理
├── virtual/     虚拟列车管理
├── map/         铁路地图
├── network/     网络同步包
├── client/      客户端渲染、GUI、粒子预览
├── irfix/       IR 内核修复
└── util/        工具类
```

## 作者

chuanshuoi9
