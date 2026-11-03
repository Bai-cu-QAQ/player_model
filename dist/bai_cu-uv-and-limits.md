# bai_cu：玩家皮肤 UV 与物理转角限制

已直接更新测试实例中的 bai_cu 模型。64×64 Classic（四像素手臂）皮肤布局；几何尺寸仍使用模型原尺寸，不会缩放成原版玩家。

- 基础头、身体、手臂、腿：对应原版基础皮肤区域。
- 模型已经存在的外套、袖子、裤腿：对应原版外层皮肤区域，不新增额外方块。
- 三片物理刘海：保留帽子正面原有裁切区域及正反面镜像关系，不把每片展开成整个头部。
- 保留 UUID、分组、物理骨骼、pivot、旋转、几何和内嵌贴图内容。
- meta 的 textureMode 已从无效的 texture 改为 skin；游戏使用玩家皮肤。Blockbench 仍预览原内嵌皮肤，想完全一致请在编辑器中换成同一张玩家皮肤。

## 可修改的最大角度（度）

在模型目录 meta.json → physics → defaultSettings 中：

```json
"angleXMin": -30,
"angleXMax": 45,
"angleYMin": -20,
"angleYMax": 20,
"angleZMin": -15,
"angleZMax": 60
```

每个轴独立限制最小值/最大值，单位度；`min == max` 锁定该物理轴。也可在 physics.bones 下某个骨骼的配置中覆盖：

```json
"hair_physics": {
  "angleXMin": -25,
  "angleXMax": 35,
  "angleYMin": -10,
  "angleYMax": 10,
  "angleZMin": -15,
  "angleZMax": 20
}
```

旧字段 `maxAngleX/Y/Z` 仍兼容，会解释为 `[-maxAngle, +maxAngle]`；新配置优先使用 `angleXMin/angleXMax`。

参数约束的是模型空间物理旋转向量 X/Y/Z 分量，不是最终局部 Euler 角；不会限制玩家基础动画、模型原有静态旋转或父骨骼继承旋转。范围仍受 maxAngleLimit 限制；某侧省略时回退到 maxAngle（cloth 时包含 clothMaxAngleScale），全部省略则完全保持旧的球形总转角限制。

## 使用

替换模组后重启游戏，再运行：

```text
/pmodel reload
/pmodel use bai_cu
```

以后只修改 meta 中角度参数时，reload 后重新选择模型即可。
原模型及配置的时间戳备份在 bai_cu/backup_skin_uv_20261025_022214/ 下。
