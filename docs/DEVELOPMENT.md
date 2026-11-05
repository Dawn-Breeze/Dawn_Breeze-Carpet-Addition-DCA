# Development notes

DCA is a [Carpet](https://modrinth.com/mod/carpet) extension: Carpet gives it the rule system
(`/dca` command, `dca.conf`, translations, client sync) and DCA supplies the gameplay through mixins.

## Layout

```
src/main/java/dca/
  DcaExtension.java              entrypoint: registers the extension and its SettingsManager
  DcaSettings.java               every rule is a @Rule-annotated public static field (default value = field initializer)
  redstone/                      rule implementations, kept out of the mixins where possible
  client/                        client-only code (key polling, focus-loss audio)
  mixin/                         mixins, one class per vanilla target
src/main/resources/
  fabric.mod.json                mod metadata, entrypoint, dependencies
  dca.mixins.json                mixin registry (a mixin missing here silently does nothing)
  assets/dca/lang/*.json         rule names/descriptions
```

## How a Carpet extension boots

1. **Discovery** — Carpet does not scan for extensions; the extension reports itself. Fabric Loader
   calls the `main` entrypoint (`ModInitializer#onInitialize`), which does
   `CarpetServer.manageExtension(new DcaExtension())`. This has to happen before the server starts,
   which is exactly what a `main` entrypoint gives.
2. **Settings manager** — `new SettingsManager(version, identifier, fancyName)`, returned from
   `CarpetExtension#extensionSettingsManager()`. Carpet registers `/dca` from `identifier`, reads and
   writes `<world>/dca.conf`, and syncs rule values to clients.
3. **Rule parsing** — `onGameStarted()` calls `settingsManager.parseSettingsClass(DcaSettings.class)`,
   which reflects over `@Rule` static fields. The **field type** decides the rule type
   (boolean/int/long/double/float/String/enum), the **field initializer** is the default, and enums
   and booleans derive their allowed values automatically.
4. **Translations** — Carpet asks `canHasTranslations(lang)` for a flat `key -> text` map:
   `<identifier>.rule.<rule>.name`, `<identifier>.rule.<rule>.desc`,
   `<identifier>.category.<category>`.
5. **Behaviour** — mixins, or the `onTick(server)` callback. Rules themselves never change game logic.

## Adding a rule

1. Add a field to `DcaSettings`:

   ```java
   @Rule(categories = {FEATURE, DCA})
   public static boolean myRule = false;

   // Numeric rules need explicit options; enums and booleans do not.
   @Rule(categories = {FEATURE, DCA}, options = {"0", "1", "2"})
   public static int myNumberRule = 0;

   public enum MyMode { OFF, SLOW, FAST }
   @Rule(categories = {FEATURE, DCA})
   public static MyMode myModeRule = MyMode.OFF;
   ```

2. Add the two translation keys to `en_us.json` and `zh_cn.json`. Nothing else has to be registered.
3. Read it from code with `if (DcaSettings.myRule) ...` — the field is written by Carpet, so do not
   cache it in a local variable across ticks.
4. Implement the behaviour in a mixin (below) or in `onTick`.

## Adding a mixin

1. Put the class in `dca/mixin/`, conventionally `<TargetClass>Mixin`.
2. Register it in `dca.mixins.json`. Client-only mixins go into the `"client"` array; putting one into
   `"mixins"` crashes a dedicated server.
3. Use the **full descriptor**, not just the method name, so a mismatch fails loudly:

   ```java
   @Inject(method = "randomTick(Lnet/minecraft/world/level/block/state/BlockState;"
                + "Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;"
                + "Lnet/minecraft/util/RandomSource;)V",
           at = @At("HEAD"), cancellable = true)
   ```

   Get the descriptor with `javap -p -s -cp <classpath> <target class>`.
4. When the target class does not declare the method (it lives in a superclass), mix into the
   superclass and guard with `instanceof`. The cast to `Object` is required because the mixin class
   has no compile-time inheritance relation to the target:

   ```java
   @Mixin(BlockBehaviour.class)
   public abstract class BlockBehaviourMixin {
       @Inject(method = "tick(...)V", at = @At("HEAD"), cancellable = true)
       private void dca$blink(BlockState state, ServerLevel level, BlockPos pos,
                              RandomSource random, CallbackInfo ci) {
           if (!((Object) this instanceof RedStoneOreBlock ore)) return;
           ...
       }
   }
   ```

5. `injectors.defaultRequire = 1` means a failed injection crashes the game at startup. That is
   deliberate — a silent no-op is worse — but re-check descriptors after a Minecraft update.

## Minecraft 26.2 specifics

26.2 ships **without obfuscation**, so there are no Yarn/intermediary mappings and mixin names must
match the official names exactly. A refmap is generated but does nothing useful. Things that differ
from older tutorials and came up while writing DCA:

* it is `RedStoneOreBlock` (capital S), not `RedstoneOreBlock`;
* `Minecraft.screen` no longer exists — use `Minecraft.getInstance().gui.screen()`;
* `SlotActionType` is now `net.minecraft.world.inventory.ContainerInput`;
* `ResourceLocation` is now `net.minecraft.resources.Identifier`;
* `GuiGraphics` is now `net.minecraft.client.gui.GuiGraphicsExtractor`;
* the client click entrypoint is
  `MultiPlayerGameMode.handleContainerInput(containerId, slot, button, ContainerInput, Player)`;
* `Screen.hasControlDown()` and friends are gone — use
  `InputConstants.isKeyDown(window, InputConstants.KEY_LCONTROL)`;
* `Player.displayClientMessage` is gone — use `ServerPlayer.sendOverlayMessage(Component)` for the
  action bar, or `sendSystemMessage`;
* `@Shadow` looks only at the target class itself, never at superclasses.

## Client-triggered rules

`villagerBulkTrade` is the pattern for "a client key press runs a server-side action": the client half
polls the key and sends one vanilla button click (`MerchantMenu`'s magic button id), and the server
half handles that click id. See `dca/client/VillagerTradeKeys.java` and
`dca/mixin/MerchantMenuButtonMixin.java`. Keep the client half to input only; all world changes belong
on the server, or a dedicated server will desync.

## Testing

Build and run a client with the jar:

```bash
./gradlew build
```

Then drop `build/libs/DCA-<version>.jar` into a 26.2 Fabric instance with Carpet and check each rule
in-game with `/dca`. The rules that need a saved world (`dca.conf` behaviour, client sync) should be
tested once in single player and once on a dedicated server when that code changes.
