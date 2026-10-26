# Hermes Replay

Watch a run back inside Minecraft, like MCSR Ranked's replay viewer, using only the files
[Hermes](https://github.com/DuncanRuns/Hermes) already writes. Nothing extra runs during the
run, so the run stays verifiable.

The world is regenerated from the run's seed. The runner is drawn from the `.ghost` file
(position, look direction, pose, health and hotbar slot every tick), wearing and holding what
the play log says they had. Blocks they broke or placed are worked out from the play log's
`mined` and `used` stats by casting a ray from the ghost's eyes, so most hand-built terrain
shows up. Explosions, fluids, mobs and dropped items don't.

## Using it

1. Type `/seed` at the end of every run. Hermes only logs the seed when you do, and without it
   there's no way to rebuild the world.
2. Put the run's `<name>.ghost` and `<name>.log` (the deciphered play log, like the ones in
   `../logs/`) in `.minecraft/hermes-replays/`.
3. Make a new 1.16.1 world with the run's seed and cheats on. The mod puts you in spectator and
   turns mob spawning off when a replay starts.
4. `/replay load <name>`.

| Key | Does |
| --- | --- |
| P | Play / pause |
| `[` and `]` | Slower / faster (0.25x to 64x) |
| Left and right arrows | Back / forward 10 seconds |
| V | Follow the runner in first person, or fly around freely |

Commands: `/replay list`, `load <name>`, `play`, `pause`, `speed <x>`, `seek <h:mm:ss>` (by the
run's IGT), `follow`, `stop`. Stopping puts every inferred block change back.

The bar at the top is the run's timeline, coloured by dimension (blue overworld, red nether,
purple end), with a yellow tick for each advancement.

## Layout

- `core/` reads Hermes files and does the block inference. No Minecraft dependency, so it builds
  and tests anywhere: `cd core && gradle test`. `RealRunTest` loads run 30 from `../logs/`.
- `mod/` is the Fabric mod for Minecraft 1.16.1. It compiles `core/` into the same jar:
  `cd mod && ./gradlew build`, then the jar is in `mod/build/libs/`.

## Status

`core/` is tested against real runs. `mod/` has not been compiled or run yet: the environment it
was written in couldn't reach Fabric's or Mojang's servers. Method names were checked against
yarn 1.16.1 mappings, but expect a round of fixes the first time it builds.
