# DCA

A small [Carpet](https://modrinth.com/mod/carpet) extension for **Minecraft 26.2** (Fabric).
It adds six rules that Carpet would not normally have, all **off by default**, configured with the
`/dca` command or the per-world `dca.conf` file.

* Language: **English** | [简体中文](README.zh-CN.md)
* Minecraft: **26.2** · Fabric Loader **0.19.3+** · Java **25** · Carpet **26.2**

## Rules

| Rule | What it does |
|---|---|
| `redstoneOreNoteBlockBlink` | Redstone ore with a note block directly above it blinks on and off. The interval is a random `n`–`5n` ticks, where `n` is the note block's note. A note of 0 leaves the ore alone. |
| `villagerBulkTrade` | In the villager trade screen, hold `Ctrl+Alt+C` to repeatedly trade the selected offer using every inventory item that can pay for it. Every product is thrown onto the ground. |
| `chorusFlowerBonemeal` | Bone meal on a chorus plant or flower gives one randomly chosen connected chorus flower exactly one natural growth tick. |
| `pauseSoundOnFocusLost` | Mutes the chosen sound categories while the game window is unfocused: background music, music + jukebox records, or everything. |
| `endPlatformShapeUpdateOnEntry` | Every arrival in the End makes the 25 obsidian blocks of the arrival platform emit one round of shape updates in all six directions — what an observer in a "PP update" detector watches for. |
| `endPortalDestinationInEnd` | An end portal used **inside the End** sends entities to the End, exactly like arriving from the Overworld. Entities with momentum keep it. |

Rules are server-side gameplay (`villagerBulkTrade` additionally needs the client mod for its key
press, and `pauseSoundOnFocusLost` is client-side audio), so in single player everything works out of
the box.

## Install

1. Install **Fabric Loader 0.19.3 or newer** for Minecraft 26.2.
2. Put **Carpet 26.2** into `mods/`.
3. Put the DCA jar from [Releases](../../releases) into `mods/`.
4. (Optional) Install [Mod Menu](https://modrinth.com/mod/modmenu); DCA then shows up under Carpet.

On a dedicated server, DCA goes into the server's `mods/` as well. `villagerBulkTrade` needs DCA on
**both** the client and the server; `pauseSoundOnFocusLost` needs it on the **client** (the rule value
is synced from the server like every Carpet rule).

## Usage

All rules start disabled. Change them at runtime (permission level 2) or persist them in the world:

```
/dca                                  # list the rules, click to change
/dca villagerBulkTrade true
/dca pauseSoundOnFocusLost music_and_records
/dca endPortalDestinationInEnd item
```

Values:

* `pauseSoundOnFocusLost`: `off`, `music`, `music_and_records`, `all`
* `endPortalDestinationInEnd`: `off`, `all`, `non_player`, `item` (items only — not XP orbs)

The rules are stored in `<world>/dca.conf` and synced to clients that also have Carpet (and DCA, for
the two client-side features).

### Bulk trading

Click the trade row you want to use, then hold `Ctrl+Alt+C`. The server repeats that trade as long as
the offer has uses left and the inventory can pay. Each product is thrown onto the ground at your
feet (vanilla's "throw" action on the result slot), so the inventory never fills up with output.

Notes:

* An offer runs out after `maxUses` trades (usually 12), so a burst normally ends with "the villager
  needs to restock".
* Only the main inventory and hotbar pay (vanilla's `MerchantMenu.tryMoveItems` looks at slots
  3–38); the off hand and armour slots are not used.
* DCA must be installed on the client that presses the key, and the rule must be enabled on the
  server.

### Focus-loss silence

`music` covers `SoundSource.MUSIC` only; a playing jukebox record is `SoundSource.RECORDS` and needs
`music_and_records`. The game is not paused — playback continues silently, so a record does not jump
back to its start when you return. The player's volume settings are never modified.

### End platform shape updates

Any entity arriving in the End through an end portal triggers one round of shape updates from the
25 obsidian blocks of the arrival platform (fixed at x 98–102, y 48, z −2…2), regardless of whether
vanilla actually reset the platform. Only the placement/direction update is emitted: no block is
replaced, nothing is dropped, and the chunk ticket behaviour is untouched.

### End portals inside the End

The rule makes an end portal used inside the End arrive in the End, the same way it would from the
Overworld. Such a portal therefore no longer takes you back: with `all` this includes players, so
**be careful not to get stuck in the End**. That is a warning, not what the rule is for — it is
there so portals inside the End behave like Overworld arrivals. With `non_player` or `item` the
player can still use the portal normally.

The arrival is built exactly like the Overworld-to-End branch: the fixed End spawn platform
(obsidian at x 98–102, y 48, z −2…2; landing spot y 49), platform reset (with drops), `DELTA` motion
preserved, rotation from `WEST`, and a `PORTAL` chunk ticket. One difference: every entity lands on
the platform itself. Vanilla places non-player entities with `Entity.adjustSpawnLocation`, which in
26.2 ignores its arguments and returns the column of the global Overworld respawn data — an
unpredictable spot in the End that also generates a chunk there. If
[carpet-ams-addition](https://github.com/Cjsah/carpet-ams-addition) is installed and its
`endPortalChunkLoadDisabled` rule is on, DCA follows it and skips the ticket.

## Build from source

Requires **JDK 25** (Minecraft 26.2 targets Java 25):

```bash
git clone <this repository> DCA
cd DCA
./gradlew build
```

The jar lands in `build/libs/DCA-<version>.jar`. Minecraft 26.2 is not obfuscated, so no mappings
are used and the built jar loads as-is.

The Carpet jar is needed only for compilation and is not bundled (Carpet is LGPL-3.0). Either drop
`fabric-carpet-26.2.jar` into `libs/`, or let Gradle download it once — see `carpet_url` in
`gradle.properties`. The Gradle wrapper is pinned to 9.2.1; if the wrapper download is slow, point
`distributionUrl` in `gradle/wrapper/gradle-wrapper.properties` at a mirror.

## Development

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for how a Carpet extension works, how to add a rule
or a mixin, and how the 26.2 (no mappings) conventions differ from older tutorials.

## License

MIT — see [LICENSE](LICENSE). Copyright (c) 2026 Dawn-Breeze.

Carpet is a separate project by gnembon and contributors, licensed under LGPL-3.0; DCA only compiles
against its API. Not affiliated with Mojang or Microsoft.
