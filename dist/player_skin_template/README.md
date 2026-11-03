# 原版玩家皮肤模板（Classic / 64×64 / 单层）

这是六个基础方块组成的 Minecraft Classic（4 像素宽手臂）玩家模板：头、身体、左右手臂、左右腿。没有帽子、外套、袖子、裤腿、披风或物理骨骼，也没有额外图层。

## 打开、预览与游戏内贴图

- 在 Blockbench 中打开同目录的 [model.bbmodel](<model.bbmodel>)。采用可编辑的 Generic / `free` 项目格式，而不是只能绘制皮肤的 `skin` 格式；本 mod 的方块解析器支持此结构。
- **Blockbench 预览显示的是内嵌的诊断皮肤，不是你的真实玩家皮肤。** PNG 已以 `data:image/png;base64,...` 写入贴图 `source`，单独复制工程也可以预览，不依赖绝对路径。
- [texture.png](<texture.png>) 是同一张 64×64 诊断贴图的外部副本，由 Pillow 绘制。每个面有 N/E/S/W/U/D 微型标签，六个身体部位使用不同底色；图集矩形左上角是红色 L、右上角绿色点、左下角蓝色点、右下角黄色点，用于发现镜像、旋转和上下颠倒。4×4 小面上的标签较密，建议最近邻放大查看。未使用的皮肤区域保持透明。
- [meta.json](<meta.json>) 默认 `textureMode: "skin"`：**游戏内使用玩家自己的皮肤**。嵌入/外部诊断图主要服务编辑器预览，不会把玩家强制变成诊断色块。
- 若要在游戏内也显示自定义模型贴图，将 `textureMode` 改为 `"model"`；此时同目录 `texture.png` 可用作模型贴图。保持 `textureWidth`、`textureHeight` 为 64。
- `doubleLayer: false` 明确关闭额外双层显示；不要改成 true。模板仅使用皮肤基础层，不包含原版皮肤外层几何。
- 将整个文件夹放入 mod 的模型目录使用；不要只复制 metadata。压缩包内自带 `player_skin_template/` 顶层目录。

## 坐标与关节

采用 Blockbench Y-up 坐标，脚底 y=0，头顶 y=32。解析器只把 Y 转为 `24-y`，不反射 X 或 Z；因此右手/右腿位于负 X，左手/左腿位于正 X。

| 部位 | from → to | pivot | 原版皮肤 UV offset |
|---|---|---|---|
| head | (-4,24,-4) → (4,32,4) | (0,24,0) | (0,0) |
| body | (-4,12,-2) → (4,24,2) | (0,24,0) | (16,16) |
| right_arm | (-8,12,-2) → (-4,24,2) | (-5,22,0) | (40,16) |
| left_arm | (4,12,-2) → (8,24,2) | (5,22,0) | (32,48) |
| right_leg | (-4,0,-2) → (0,12,2) | (-2,12,0) | (0,16) |
| left_leg | (0,0,-2) → (4,12,2) | (2,12,0) | (16,48) |

## 为什么不用普通递增矩形 / box UV

工程和每个方块均设置 `box_uv: false`，真正生效的是六个面的显式 UV，`uv_offset` 仅保留原版布局参考。没有 UV 旋转、mirror 或 inflate。

已检查 upstream Blockbench `skin.ts` 的 Steve 基础皮肤区域，以及 `cube.js` 的 `UVToLocal`：

- [skin.ts（固定提交）](https://github.com/JannisX11/blockbench/blob/ec0b2827104d0799c153bfb5f867579c0c44652f/js/formats/minecraft/skin.ts)
- [cube.js（固定提交）](https://github.com/JannisX11/blockbench/blob/ec0b2827104d0799c153bfb5f867579c0c44652f/js/outliner/types/cube.js)

注意 upstream skin codec 会反射预设 X；本 mod 的解析器只反射 Y。直接照抄 skin preset 经 codec 处理后的左右位置或 box-UV 面端点，会使预览与原版 Cuboid 的空间角点 UV 不一致。本模板按 **原版 Cuboid 每个空间角点的 UV**，再用 Blockbench `UVToLocal` 的面方向反推端点。

令 `(u,v)` 为皮肤区域起点，`w,h,d` 为方块宽、高、深；端点顺序为 `[u1,v1,u2,v2]`，允许降序，不能擅自排序：

| Blockbench 面 | 显式端点 |
|---|---|
| north | `[u+d+w, v+d, u+d, v+d+h]` |
| east | `[u+2d+w, v+d, u+d+w, v+d+h]` |
| south | `[u+2d+2w, v+d, u+2d+w, v+d+h]` |
| west | `[u+d, v+d, u, v+d+h]` |
| up | `[u+d, v+d, u+d+w, v]` |
| down | `[u+d+w, v, u+d+2w, v+d]` |

例如头部：N=`[16,8,8,16]`、E=`[24,8,16,16]`、S=`[32,8,24,16]`、W=`[8,8,0,16]`、U=`[8,8,16,0]`、D=`[16,0,24,8]`。四个侧面的 U 降序和 U（up）的 V 降序是有意的，目的是使 Blockbench 预览和原版空间角点方向一致，而不是简单交换皮肤矩形。

本模板已由独立 Python 空间角点校验和 Java 原版 `ModelPart.Cuboid` 对照测试验证：6 个方块 × 6 个面 × 4 个角点，共 144 个空间角点的 UV 全部通过。

[per_face.bbmodel](<per_face.bbmodel>) 是同内容的显式面 UV 副本，供 UV 回归检查；日常编辑只需主工程。自动化测试 fixture 是仓库测试资源中的独立同字节副本，PNG 内嵌，不依赖本文件夹。
