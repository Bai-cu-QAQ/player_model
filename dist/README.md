# Player Model Importer（玩家模型工坊）

Minecraft **1.21.1 / Fabric** 的玩家模型替换模组：文件夹加载 Blockbench 玩家模型，保留原版 14 骨骼与动作包动画。

## 0. 版本兼容提醒

目标实例里的 Iris `1.8.14-beta.1` 与 Sodium `0.8.13` 在 1.21.1 + Fabric Loader 0.20 下会崩溃：

- Iris：`MixinLevelRenderer` 的 `@ModifyArg` 反混淆 `desc is null`；
- Sodium：区块构建时 `BakedModel is null`。

建议使用 `dist/compat` 里的 Sodium 0.6.9 + Iris 1.8.8。  
`DetailedAnimationsReworked V1.15` 过旧，建议禁用；Fresh Moves 与 FA+Player 都是玩家 CEM 包，二选一启用。

## 1. 安装

- Minecraft 1.21.1、Fabric Loader >= 0.16、Java 21、Fabric API。
- jar 放进 `mods/`。服务端也放同一个 jar 可同步其他玩家模型。
- 首次进游戏执行 `/pmodel template`。

## 2. 文件夹加载

```
config/playermodel/models/
  my_model/
    model.bbmodel
    texture.png
    meta.json
```

- 启动时扫描；修改后 `/pmodel reload`。
- 多贴图自动合并图集；`texture.png` 只用于单贴图覆盖。
- `meta.json`：

```json
{
  "displayName": "我的模型",
  "textureMode": "model",
  "pivotMode": "model",
  "textureWidth": 64,
  "textureHeight": 64
}
```

`textureMode`：`model` / `skin`。`pivotMode`：`model`（小体型）/ `vanilla`。

`meta.json` 支持 `//` 行注释和 `/* ... */` 块注释；完整字段示例见 `dist/meta.example.jsonc`。

## 3. 模板

`/pmodel template` 生成：

- `template/`：原版比例
- `template_slim/`：细手臂（Alex）
- `template_small/`：0.6 倍小体型
- `example_small/`：示例

全部为 Blockbench Y-up 正立坐标（脚 y=0、头 y=24）。  
模板中的 `anchor_*` 分组用于调整盔甲、手持物、披风的位置和缩放。

### 3.1 锚点调整

模板里有：

- `anchor_helmet`、`anchor_chest`、`anchor_legs`、`anchor_boots`
- `anchor_right_hand`、`anchor_left_hand`
- `anchor_cape`

默认 `customAnchors=false`：锚点位置不参与 feature 对齐，只使用锚点 marker 的 scale，避免旧模板锚点把盔甲/手持物推歪。需要自由调位置时，在 `meta.json` 设 `"customAnchors": true`。

每个锚点分组下有一个 `*_size` 标记方块：

- **移动分组原点/Pivot** → 改变该 feature 的位置；
- **缩放/改大小 `*_size` 方块** → 改变该 feature 的缩放（默认 4×4×4 = scale 1；小体型模板默认 2.4 => 0.6）；
- 标记方块本身不会被渲染。

也可以直接写进 `meta.json`：

```json
"anchors": {
  "chest": { "x": 0, "y": 0, "z": 0, "scale": 0.6 },
  "right_hand": { "x": -3, "y": 7, "z": 0, "scale": 0.6 },
  "cape": { "x": 0, "y": 0, "z": -2, "scale": 0.6 }
}
```

Y/Z 位置对腿甲/靴子生效，X 不套用中心锚点，避免左右靴被推向一边；头盔/胸甲支持完整 X/Y/Z 偏移。

### 3.2 物理骨骼 `_physics`

只接受严格后缀 `_physics`：

```text
hair_physics
skirt_physics
Hair_Physics
```

`hairphysics`、`hair.physics`、`hair-physics` 都不会被识别。物理分组必须放在原版骨骼或另一个物理骨骼下面：

```text
head
  hair_physics
    hair_mid_physics
body
  skirt_physics
      skirt_tip_physics
```

物理骨骼不会并入 14 核心骨骼，方块会保持独立。支持两种模式：

- `pendulum`：单骨骼弹簧/重力/惯性；
- `cloth`：由连续 `_physics` 骨骼组成的链式布料。

配置写在模型 `meta.json`：

```json
"physics": {
  "defaultSettings": {
    "mode": "pendulum",
    "stiffness": 90,
    "damping": 14,
    "gravity": 0.6,
    "inertia": 0.8,
    "maxAngle": 55,
    "wind": 0.0,
    "disablePitch": false,
    "disableYaw": false,
    "disableRoll": false
  },
  "bones": {
    "skirt_physics": {
      "mode": "cloth",
      "maxAngle": 75,
      "disableRoll": true
    }
  }
}
```

`disablePitch` / `disableYaw` / `disableRoll` 可按骨骼禁用对应轴的物理效果：

- `disablePitch`：禁用 X 轴（前后/上下拖尾）；
- `disableYaw`：禁用 Y 轴（左右转向）；
- `disableRoll`：禁用 Z 轴（侧向摆动）。

未写时默认 `false`。

`cloth` 需要至少两个连续的 `_physics` 骨骼；单骨骼使用 `cloth` 时会按 `pendulum` 处理。物理只做视觉，碰撞第一阶段不做，不隐藏原版披风。

### 3.3 演示模型

`dist/demo_physics/` 里有一个小体型演示模型：

- `hair_physics` → `hair_tip_physics`：两段头发物理链；
- `skirt_physics` → `skirt_tip_physics`：两段裙摆物理链；
- 两段都使用 `cloth` 模式。

使用：

1. 把整个 `demo_physics` 文件夹复制到 `config/playermodel/models/`；
2. `/pmodel reload`；
3. `/pmodel use demo_physics`。

`dist/demo_physics_latest.zip` 是同一个文件夹的最新压缩包。

## 4. 渲染与 feature 对齐

- 自定义模型是独立的 `PlayerEntityModel`，基体使用它渲染；
- 通过 `FeatureRenderer.getContextModel()` 重定向，让盔甲、手持物、披风等 feature 也读取**同一个自定义模型**；
- 因此盔甲复制的是自定义 pivot/旋转，手持物用自定义手臂，披风用自定义 cloak；
- 披风使用自定义模型的 cloak（保留原版 cape 几何），按 rig scale 缩放并放在身体背面，避免消失/乱飞；
- 第一人称手臂仍使用原版皮肤；
- EMF/CEM 动画：先调用 `EMFModelPartRoot.animate()`，再把 live 骨骼变换复制到自定义模型。

## 5. 命令

| 命令 | 作用 |
|---|---|
| `/pmodel list` | 列出模型 |
| `/pmodel use <名字>` | 切换模型（自动补全） |
| `/pmodel reload` | 重新扫描 |
| `/pmodel self on/off` | 同步自己的模型开关 |
| `/pmodel view on/off` | 本地显示开关 |
| `/pmodel import <路径>` | 导入 bbmodel |
| `/pmodel validate <路径>` | 校验 |
| `/pmodel template` | 写出模板 |
| `/pmodel folder` | 模型目录 |
| `/pmodel status` | 状态 |
| F6 | 快速切换 view |

## 6. 模型限制

- 只支持方块，不支持 mesh。
- 支持 box UV / per-face UV、inflate、mirror_uv、面 UV 旋转。
- 支持 cube 静态旋转（X→Y→Z，绕 cube origin）。
- 支持骨骼静态旋转：导入后与动画/物理旋转组合，不会被动画覆盖。
- 骨骼缩放仍不支持；请在 Blockbench 中应用缩放后再导入。
- 核心 6 骨骼必需：head、body、左右手臂、左右腿。
- 额外分组会合并到最近的 14 骨骼之一。

## 7. 已知限制

- 第一人称手臂仍是原版几何 + 皮肤。
- 碰撞箱、眼睛高度、名字标签、披风/鞘翅/手持物尺寸仍受原版/EMF 模型影响。
- 小体型的手臂长度与物品原始挂点可能存在细微差异。

## 8. 构建

```bash
gradle build
```

Gradle 8.12、Java 21、Loom 1.9.2、Yarn 1.21.1+build.3、Loader 0.20.0、Fabric API 0.116.17+1.21.1。  
产物：`build/libs/playermodel-1.0.0.jar`。
