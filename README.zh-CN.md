# DCA

一个面向 **Minecraft 26.2**（Fabric）的 [Carpet](https://modrinth.com/mod/carpet) 小型扩展，加入
六条原版 Carpet 没有的规则。全部**默认关闭**，用 `/dca` 命令或存档内的 `dca.conf` 开关。

* 语言：[English](README.md) | **简体中文**
* 游戏版本：**26.2** · Fabric Loader **0.19.3+** · Java **25** · Carpet **26.2**

## 规则

| 规则 | 作用 |
|---|---|
| `redstoneOreNoteBlockBlink` | 正上方有音符盒的红石矿石会随机切换亮灭，间隔为 `n`–`5n` 刻（`n` 为音符盒的音符值）；音符为 0 时不处理。 |
| `villagerBulkTrade` | 在村民交易界面按住 `Ctrl+Alt+C`，用背包里所有能支付的物品反复成交当前选中的交易；每一份产物都直接甩到地上。 |
| `chorusFlowerBonemeal` | 对紫颂茎或紫颂花使用骨粉，使相连结构里随机一朵紫颂花获得一次自然生长。 |
| `pauseSoundOnFocusLost` | 游戏窗口失去焦点时静音指定类别：背景音乐、背景音乐+唱片、或全部声音。 |
| `endPlatformShapeUpdateOnEntry` | 每次抵达末地时，让抵达平台的 25 块黑曜石向六个方向各发出一次形状更新（即「PP 更新」侦测器所侦测的内容）。 |
| `endPortalDestinationInEnd` | 在**末地内**使用末地传送门时，把实体送往末地，效果与从主世界进入完全相同；实体的动量会保留。 |

规则都是服务端行为（`villagerBulkTrade` 还需要客户端提供按键，`pauseSoundOnFocusLost` 是客户端音频
逻辑），单人游戏开箱即用。

## 安装

1. 为 Minecraft 26.2 安装 **Fabric Loader 0.19.3 或更新版本**。
2. 把 **Carpet 26.2** 放进 `mods/`。
3. 把 [Releases](../../releases) 里的 DCA jar 放进 `mods/`。
4. （可选）安装 [Mod Menu](https://modrinth.com/mod/modmenu)，DCA 会归到 Carpet 下面显示。

专用服务器也把 DCA 放进服务器端的 `mods/`。`villagerBulkTrade` 需要**客户端和服务端都装** DCA；
`pauseSoundOnFocusLost` 需要**客户端装**（规则值像其它 Carpet 规则一样由服务端同步）。

## 使用

所有规则初始都是关闭的。可以在游戏内直接改（权限等级 2），也可以写进存档配置：

```
/dca                                  # 列出规则，点选修改
/dca villagerBulkTrade true
/dca pauseSoundOnFocusLost music_and_records
/dca endPortalDestinationInEnd item
```

可选值：

* `pauseSoundOnFocusLost`：`off`、`music`、`music_and_records`、`all`
* `endPortalDestinationInEnd`：`off`、`all`、`non_player`、`item`（仅物品实体，不含经验球）

规则保存在 `<存档>/dca.conf`，并同步给同样装了 Carpet（以及 DCA）的客户端。

### 喷射交易

先点选想用的交易行，然后按住 `Ctrl+Alt+C`。服务端会反复成交，直到该交易次数用完或背包付不起；
每一份产物都直接甩到地上（原版结果槽的「丢弃」动作），所以背包不会被产物塞满。

注意：

* 一条交易在 `maxUses` 次（通常 12 次）后会售罄，所以通常以“村民需要补货”结束。
* 只用主背包和快捷栏支付（原版 `MerchantMenu.tryMoveItems` 只看 3–38 号槽位），副手和盔甲槽不参与。
* 需要按按键的客户端装 DCA，并且服务端把规则打开。

### 失焦静音

`music` 只覆盖 `SoundSource.MUSIC`；正在播放的唱片属于 `SoundSource.RECORDS`，需要
`music_and_records`。游戏不会暂停，声音是“静音播放”，所以回焦后唱片不会从头开始；玩家的音量设置
不会被改动。

### 末地平台的形状更新

任何实体经末地传送门抵达末地时，都会让抵达平台的 25 块黑曜石（固定坐标 x 98–102、y 48、z −2…2）
向六个方向发出一次形状更新，无论原版是否真的重建了平台。只发出放置/方向更新：不替换方块、不掉落
物品，也不改变区块加载票。

### 末地内的末地传送门

这条规则让在末地内使用的末地传送门也把实体送到末地，抵达方式与从主世界进入完全一致。因此这种传送门
不再把人送回主世界：选 `all` 时玩家也一样，所以**小心被困在末地**。这只是提醒，并不是规则的目的
——规则的目的就是让末地内的传送门表现得像主世界进入。选 `non_player` 或 `item` 时玩家仍能正常使用
传送门。

抵达过程与原版“主世界→末地”分支一致：使用固定的末地出生平台（黑曜石 x 98–102、y 48、z −2…2，
落点 y 49）、平台重建（会掉落原方块）、保留 `DELTA` 动量、`WEST` 朝向，并添加 `PORTAL` 区块加载票。
唯一的差别是所有实体都直接落在平台上：原版对非玩家实体调用 `Entity.adjustSpawnLocation` 决定落点，
而 26.2 的该方法会忽略传入参数、返回全局主世界重生点所在的列（还会在末地生成那一列区块），位置不可
预期。如果装了 [carpet-ams-addition](https://github.com/Cjsah/carpet-ams-addition) 且其
`endPortalChunkLoadDisabled` 规则开启，DCA 会跟随它、不加加载票。

## 从源码构建

需要 **JDK 25**（Minecraft 26.2 使用 Java 25）：

```bash
git clone <本仓库> DCA
cd DCA
./gradlew build
```

产物在 `build/libs/DCA-<版本>.jar`。26.2 没有混淆，因此不需要 mappings，构建出的 jar 可直接加载。

Carpet 只在编译期需要，不会打进 jar（Carpet 是 LGPL-3.0）。把 `fabric-carpet-26.2.jar` 放进
`libs/`，或让 Gradle 自动下载一次（见 `gradle.properties` 里的 `carpet_url`）。Gradle 包装器固定为
9.2.1；如果下载慢，可以把 `gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl` 换成镜像。

## 开发

Carpet 扩展的运行机制、如何新增规则/Mixin、以及 26.2（无映射）与旧教程的差异，见
[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)。

## 许可证

MIT，见 [LICENSE](LICENSE)。版权所有 (c) 2026 Dawn-Breeze。

Carpet 是 gnembon 及其贡献者的独立项目（LGPL-3.0），DCA 只针对其 API 编译。本项目与 Mojang /
Microsoft 无关。
