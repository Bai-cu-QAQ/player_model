# 分支维护与验证

## 分支职责

- `main`：主版本，Minecraft 1.21.1 / Fabric；本次已快进同步到 `1.21.1` 的 `411b6e0`。
- `1.21.1`：保留的 1.21.1 版本分支。本次整理只修改 `main` 与 `1.21.11`，没有移动该分支。
- `1.21.11`：Minecraft 1.21.11 / Fabric 的独立适配分支，不直接合并主版本构建文件或 Minecraft API 调用。

## 共享逻辑与版本边界

JPM/JEM 解析、表达式求值、原始模型的数据转换应尽量保持一致。导入入口使用 `RawModel.invertXAxis()`，而不是负缩放或旧的局部肢体镜像入口；不要同时调用新旧转换，避免二次反转。旧镜像方法暂时保留供既有调用与测试使用，内部复用方块转换函数。

版本相关代码必须分别验证：

| 部分 | main / 1.21.1 | 1.21.11 |
| --- | --- | --- |
| 构建工具 | Gradle 8.12 / Loom 1.9.2 | Gradle 9.3 / Loom 1.14.10 |
| 骨骼坐标 API | `setPivot` / `pivotX` | `setOrigin` / `originX` |
| 模型渲染 | 即时 `VertexConsumer` | `OrderedRenderCommandQueue`，提交私有姿态快照 |
| 准星入口 | `GameRenderer.updateCrosshairTarget` | `ClientPlayerEntity.getCrosshairTarget` |

不得直接覆盖版本各自的 UV 校准公式、mixin 目标签名、Fabric 依赖和渲染状态实现。

## 验证

使用 Java 21，在对应分支工作目录中执行：

```powershell
# main（仓库现有本地工具）
.\.tools\gradle-8.12\bin\gradle.bat test build --console=plain

# 1.21.11（使用本机已安装的 Gradle 9.3）
& '<Gradle 9.3 安装目录>\bin\gradle.bat' test build --console=plain
```

新增的 `RawModelXAxisTest` 检查普通骨骼、物理骨骼、方块、旋转、东西面 UV 和锚点的转换，以及连续反转两次的数据恢复。

构建/单元测试不能证明游戏内相机射线、EMF 动画和延迟渲染完全正确。发布前仍需分别检查：第一/第三人称、背包预览贴图、转头与面部锚点、蹲下/游泳眼高、方块和实体拾取。

## 本次审计的待处理项

以下风险来自 1.21.11 源码审查，本次未改动其行为，不能视为已修复：

- 准星光学拾取同时作用于第三人称前后视角，需核实交互距离和遮挡语义。
- JEM 同一身体骨骼多个附件使用相同子节点名，后项可能覆盖前项；头部/headwear 的多附件与 `attach` 语义需要专项修复和测试。
- 重扫模型会重建 `PreparedModel`，但 `AppearanceManager` 的模型缓存没有相应失效清理，需明确重载/断开连接生命周期。

## 产物与提交

- `build/libs` 为当前源码的构建结果；`dist` 中已跟踪的 JAR 是已有发布快照，不应在纯代码整理时误认为已重新发布。
- 不在整理代码时改动用户游戏实例、模型配置或关闭游戏进程。
- 两个版本可使用独立 Git worktree 验证；不要让两个任务并发写同一个构建目录。
- 提交前检查 `git diff --check` 和对应版本的完整测试；不要混入另一版本 JAR 或依赖配置。
