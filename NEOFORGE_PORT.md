# NeoForge 1.21.1 原生移植（开发阶段）

> **当前不是功能移植完成版，不替换玩家模型，不应发布。**
> 本分支第一阶段仅建立原生 NeoForge 启动入口、独立构建和可共享核心测试。
> Fabric 渲染/网络/UI 代码仍保留作为后续迁移参考，但不进入 NeoForge jar。

## 官方依据与工具链

- 官方 NeoForge 1.21.1 ModDevGradle MDK：
  https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle
- 构建模板：
  https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle/blob/main/build.gradle
- 版本模板：
  https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle/blob/main/gradle.properties
- 本阶段固定 ModDevGradle `2.0.148`、NeoForge `21.1.265`、Minecraft `1.21.1`、Java `21`。
- 官方当前 MDK wrapper 为 Gradle `9.2.1`；本机使用已有 Gradle `9.3.0` 验证，无需改动 Fabric 构建或其工具链。没有验证 Gradle `8.12` 或其他 Gradle 版本。
- 使用 ModDevGradle 默认的 **Mojang 官方映射**；没有配置 Yarn、Parchment、Fabric Loom、Connector 或 Fabric API。
- `neoforge/` 是独立 Gradle build，不是根 Fabric build 的子项目；必须带 `-p neoforge`，否则运行的是旧 Fabric 构建。

## 已实现的代码边界

- `neoforge/src/main/java/dev/playermodel/neoforge/PlayerModelNeoForge.java`：真正的 `@Mod("playermodel")` 入口，注册 NeoForge common setup 生命周期监听。
- `META-INF/neoforge.mods.toml`：原生 `javafml` 元数据，限定 Minecraft 1.21.1，并明确显示 Development Port。
- 入口启动时输出开发阶段警告；不会注册尚未迁移的玩家模型、网络处理器、UI、渲染器或 mixin。
- 构建版本 `1.0.0-dev-mc1.21.1`，输出 `playermodel-neoforge-1.0.0-dev-mc1.21.1.jar`。
- 没有应用 `maven-publish`，没有发布任务，也不复制产物至 `dist/` 或游戏 mods 文件夹。

### 共享代码（不复制实现）

`sourceSets.core` 直接读取仓库 `src/main/java`，通过 includes 白名单选取：

- `jpm/**`：JPM/JEM 解析、表达式与动画上下文。
- `model/**`：纯 Java 模型数据结构、物理配置与 bundle。
- `importing/**`：Blockbench 导入和模型空间工具。
- 图集 `compile/TextureAtlas.java` 虽使用 Java 图像 API，但依赖 `ModelCompiler.CompileException`，因此本阶段仍排除，不伪装成可独立核心。
- `AngularMotion`、`SecondaryForces`、`ModelCameraHeight`、`JsonComments`、`net/Chunking`。

`core` 仅依赖 Gson `2.10.1` 与 JOML `1.10.5`，不依赖 Minecraft、NeoForge 或 Fabric。
主 jar 包含该 core 输出，但不内嵌 Gson/JOML（由 Minecraft 环境提供）。

唯一需要对现有共享源码作的解耦是 `JpmExpressionParser` 的 Yarn `MathHelper.wrapDegrees(float)`：替换为本地等价的 float 取模与边界处理，保留原 JOML 弧度换算和 float 精度行为；新增角度边界/非有限值回归测试。其他核心实现没有复制或重写。

### 明确排除

- 原 Fabric `PlayerModelMod`、所有 `client/**`、`mixin/**`、`runtime/**`、`server/**`。
- Fabric `net/Networking` 与 `net/Payloads`，以及使用 Yarn `ModelPart`/`Direction` 的 `compile/ModelCompiler`。
- 所有根 `src/main/resources`：不带入 `fabric.mod.json`、Fabric mixin JSON 或 access widener。
- 依赖 Yarn 客户端模型的测试不参与本阶段测试；不能用核心测试通过来声称渲染、模型编译或联网已完成。

## 构建和测试

从此 worktree 根目录的 PowerShell（Java 21）：

```powershell
$gradle = 'C:\Users\Administrator\.gradle\wrapper\dists\gradle-9.3.0-bin\79n14ral3mx1ozqr3csh2u872\gradle-9.3.0\bin\gradle.bat'
& $gradle -p neoforge clean build --console=plain
& $gradle -p neoforge coreTest test --rerun-tasks --console=plain
```

单独验证不需要 Minecraft 类路径的核心编译与测试：

```powershell
& $gradle -p neoforge compileCoreJava coreTest --console=plain
```

`coreTest` 复用 8 个已有纯 Java 测试类（物理数学、配置、模型数据、JPM/JEM 解析与上下文），另含角度解耦回归测试；`test` 单独检查原生入口注解、事件总线构造签名和元数据。`check` 自动执行两组测试及 `verifyPortJar`，后者检查输出名、原生入口/元数据存在性以及 Fabric/未迁移类和类字节码中的 loader/game 耦合。未迁移的 `JustExpressionsCompatTest` 因构造 `runtime.JpmFaceModel` 被排除。

开发启动（会启动新的独立开发实例，不要把 dev jar 装入现有游戏）：

```powershell
& $gradle -p neoforge runClient --console=plain
& $gradle -p neoforge runServer --console=plain
```

- run 目录隔离于 `neoforge/run/client`、`neoforge/run/server`。
- 第一次构建需要下载 NeoForge/Minecraft 开发依赖，速度依赖网络与缓存。
- 本项目没有新增 Gradle wrapper；在其他机器上需安装兼容的 Gradle 和 Java 21，使用 `gradle -p neoforge ...`。
- 测试结果：`neoforge/build/reports/tests/coreTest/index.html`、`neoforge/build/reports/tests/test/index.html`，对应 XML 在 `neoforge/build/test-results/`。
- 本机 Windows 系统代理已配置为 `127.0.0.1:7897`；JVM 不会自动采用该设置。若直连 Maven 超时，可在命令中显式传入 `'-Dhttps.proxyHost=127.0.0.1' '-Dhttps.proxyPort=7897' '-Dhttp.proxyHost=127.0.0.1' '-Dhttp.proxyPort=7897'`（PowerShell 必须保留引号）。这只是本机网络参数，不写入仓库或全局 Gradle 配置；其他机器请使用自身代理配置。
- 共享测试使用 worktree 根目录为工作目录；依赖未跟踪 `.tools/JustExpressions` 资源包的已有用例会按原测试假设跳过，不会伪造成功。

## 本机验证记录

在此独立 worktree 使用 Java `21.0.4`、Gradle `9.3.0` 实测：

- `clean build --console=plain`：**BUILD SUCCESSFUL**（最终完整构建 14 秒；首次 NeoForm/Mojang 下载、映射、反编译及重编译另需时间）。
- `coreTest`：**40 项，37 通过、3 跳过、0 失败/错误**。跳过的是原有真实 JustExpressions 资源包用例：`parsesRealJustExpressionsJemInlineParts`、`parsesAndAnimatesRealJustExpressionsFace`、`parsesJemAndResolvesFaceModel`；本 worktree 没有该未跟踪资源包。
- `test`：**2 项全部通过**（原生 `@Mod`/构造签名与扩展后元数据）。这些是静态/类路径检查，不等于客户端进游戏或渲染验收。
- `verifyPortJar`：**通过**，实际产物 `neoforge/build/libs/playermodel-neoforge-1.0.0-dev-mc1.21.1.jar`；没有打入 Fabric 入口、资源、未迁移渲染/网络实现或指向 Yarn 的类引用。
- 独立 `coreTest --offline --rerun-tasks --console=plain`：**BUILD SUCCESSFUL**（2 秒，3 个任务重新执行），任务图仅核心编译/测试，没有 `createMinecraftArtifacts`。
- `git diff --check`：通过。`git check-ignore` 确认 `neoforge/.gradle/`、`neoforge/build/`、`neoforge/run/` 均被现有非锚定规则忽略。
- Gradle 提示有面向 Gradle 10 的弃用警告；不影响本次 Gradle 9.3 构建，但没有宣称兼容 Gradle 10。
- 没有运行 Fabric 构建，没有停止现有游戏，没有安装或发布 dev jar。

**尚未做原生客户端/专服游戏启动验证。** 曾尝试 `runServer`，但 ModDevGradle 在 `downloadAssets` 阶段默认准备数千项资产；按 Lead 要求，在资源准备阶段取消该自建验证 job，没有进入原生游戏进程，也没有证明 `@Mod` 构造或 common setup 日志在真实游戏启动中执行。没有接受 EULA、启动客户端、绑定游戏服务器端口或停止任何既有游戏。本轮“原生骨架验证”仅指真实 NeoForge API 编译、原生元数据/入口测试及 jar 边界检查，不等于游戏启动/模型功能验收。

## 下一阶段（尚未实现）

1. 将模型编译器和 runtime 的 Yarn API 转成 Mojang 映射，建立 NeoForge 渲染生命周期/玩家 renderer 集成。
2. 原生移植外观注册、纹理生命周期、模型选择 UI、配置路径；决定客户端与服务端职责。
3. 迁移自定义 payload、连接/断开事件与服务器同步；验证单机、多人及专服隔离。
4. 按 Mojang 类名逐一审计相机、护甲、披风、手持物品和玩家模型相关 mixin，不能直接复用 Fabric mixin 配置。
5. 迁移物理更新调度、EMF/其他模组兼容集成，并补渲染和端到端回归测试。
6. 只有实际完成功能与游戏验证后再制定 NeoForge 发布版本；当前 dev jar 不发布。
