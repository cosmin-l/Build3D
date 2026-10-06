# Build3D

A small 3D engine in pure Java, built around the same core trick as Ken
Silverman's **Build** engine (Duke Nukem 3D, Shadow Warrior): the world is a
2D map of **sectors** (rooms) with independent floor and ceiling heights,
connected by **portals**. There is no true 3D geometry and no BSP tree —
each frame the renderer walks outward from the sector the player is
standing in, through portals, drawing only what's still visible through the
shrinking on-screen window left open by nearer walls. That's what lets
rooms sit above, below, or behind one another with variable floor/ceiling
heights ("room over room"), which a plain raycaster (Wolfenstein-style)
can't do.

It's a software renderer — every pixel is computed on the CPU into an
`int[]` framebuffer and blitted to the screen — no OpenGL/LWJGL, no external
dependencies, just the JDK.

## Running

Requires a JDK (25 was used to build this; anything reasonably recent works).

```powershell
.\run.ps1                    # builds and runs maps/sample.map
.\run.ps1 maps/mymap.map     # run a different map
```

or on a POSIX shell:

```bash
./run.sh
./run.sh maps/mymap.map
```

## Controls

- `W A S D` — move / strafe
- Mouse — look (click the window once to capture the mouse; `Esc` releases it)
- `Left` / `Right` arrows — turn without the mouse
- `Up` / `Down` arrows — look up/down without the mouse
- `Shift` — run
- `Space` — jump
- `Tab` — toggle a top-down minimap overlay
- `1`-`6` / mouse wheel — switch weapon (only among weapons you've picked up)
- Left mouse button — fire the current weapon (hold for full-auto weapons)

## How it works

- **`Sector`** — a closed polygon of `Wall`s plus a floor height, ceiling
  height, and colors. Vertices must be wound counter-clockwise as seen from
  above (interior on the left as you walk each wall from its first vertex to
  its second).
- **`Wall`** — one edge of that polygon. `portal >= 0` means "this edge opens
  into sector `portal`"; `portal == -1` means it's solid.
- **`Renderer`** — the portal-traversal software rasterizer. For each
  column of the screen it tracks the still-open vertical window
  (`topOpen[x]`/`bottomOpen[x]`); a solid wall fills that window and closes
  the column, a portal wall only fills the part outside the *overlap* of the
  two sectors' floor/ceiling heights (drawing the ceiling/floor flats and any
  step "riser" walls for the mismatched part) and then recurses into the
  neighbor sector for whatever's left open. That's the entire "room over
  room" trick — it falls out of `openTop = max(ceilY, neighborCeilY)` /
  `openBottom = min(floorY, neighborFloorY)` in screen space.
- Looking up/down is a **y-shear** (a pixel offset added after projection),
  not a real camera pitch rotation — this is the actual technique the Build
  engine used, not an approximation of it.
- Walls and floors/ceilings can use either cheap procedural "textures"
  (`Textures.java`: brick, panel, stripes — just math, no image assets) or
  real image files loaded via `javax.imageio` (`ImageTexture.java`,
  registered per map with `IMAGE id path [tileFeet]`). Floors and ceilings
  are genuinely floor-cast: for a textured flat, each screen pixel's world
  (x, y) is derived from its screen row via the inverse of the wall
  projection, then sampled — not just a flat fill. Sprites can likewise
  carry an image texture with alpha-cutout transparency instead of a solid
  color box. Distance-based darkening applies uniformly for depth cueing.
- **Voxel sprites** (`VoxelModel`/`VoxelLoader`/`VoxelSprite`) are a
  Build-engine-style alternative to flat billboards: a small 3D grid of
  colored cubes (a sparse text format, `DIM`/`SCALE`/`V x y z color`),
  placed in the world with `VOXSPRITE x y sector baseZ yawDeg scale
  modelId`. Each frame, every occupied voxel in view is projected and
  depth-sorted individually and drawn as a flat-shaded screen-space square
  (a cube "splat", not a true per-voxel raycast) — cheap, and gives correct
  parallax/self-occlusion and rotation for prop-sized models.
- Collision is deliberately simple: each tick, a candidate position is
  tested with a point-in-polygon check against every sector (`GameMap
  .findSector`), preferring continuity with the current sector and, when
  sector footprints overlap in XY, the one whose floor height is closest to
  where the player already is. Crossing into a neighboring sector is only
  allowed if the floor step is small (`Player.STEP_MAX`, an 8-Duke-unit-ish
  24) and the neighbor has headroom (`Player.HEIGHT`); a separate
  closest-point-on-segment push-out (`Game.resolveRadius`) keeps the player
  `Player.RADIUS` units from solid walls. `Space` jumps (an initial upward
  velocity) and gravity pulls the eye back down to the current sector's
  floor, clamped so the player's head doesn't clip through the ceiling;
  stepping off a ledge falls the same way once the drop is more than a
  half-unit, while small steps (stairs) still snap up smoothly.
- Sprites (`Sprite`) are flat-shaded camera-facing billboards, depth-tested
  per screen column against a `wallDepth[]` buffer recorded while drawing
  walls, then painted back-to-front.
- **Weapons** (`WeaponType`/`WeaponSystem`/`WeaponRenderer`) are a
  retro-FPS arsenal in look, feel, and functionality: Mighty Foot, Pistol,
  Shotgun, Chaingun Cannon, RPG, and Devastator, picked with `1`-`6` or the
  mouse wheel. The sounds aren't ours to ship, and neither is Duke 3D's own
  art, so the viewmodel sprites (idle/fire/pump/reload frames, blitted with
  nearest-neighbor scaling, no anti-aliasing) are real pixel art sourced
  from the Freedoom project's BSD-licensed Doom sprite replacements (see
  `textures/weapons/FREEDOOM_LICENSE.txt`) -- Devastator borrows Doom's
  Super Shotgun for its twin-barrel look. The *behavior* matches Duke's
  feel closely regardless: the chaingun and devastator spin up before
  reaching full rate, the shotgun pumps between shots, the pistol
  auto-reloads after a 12-round clip, running out of ammo auto-switches to
  the next-best owned weapon, and picking up a weapon for the first time
  shows a Duke-style "Got the ...!" message. Pistol/shotgun/chaingun fire
  are hitscans (a `GameMap.raycast` march through sectors/portals) that
  spawn a spark where they hit; RPG/Devastator rockets are real projectiles
  (`Projectile`) that fly through the sector/portal geometry and explode
  (with a screen shake if you're close) on hitting a wall, floor, or
  ceiling. Explosions, sparks, and smoke (`Effect`) are short-lived dynamic
  billboards merged into the normal sprite-rendering pass each frame, so
  they're depth-tested against walls for free. Weapon/ammo pickups
  (`Pickup`, a `PICKUP` map directive) sit in the world and are collected
  by walking within radius of them.

## Map format

Plain text, loaded by `MapLoader`. See `maps/sample.map` for a full example
(a hall, a corridor into a tall pillared room, a 4-step staircase up to a
balcony, and a sunken pit room).

```
PLAYER x y angleDeg sector [eyeHeight]

SECTOR id floor ceil floorColor ceilColor [light] [floorTex] [ceilTex]
WALL x1 y1 x2 y2 portal color [texId]
...
ENDSECTOR

SPRITE x y sector height color scale [imageTex]

IMAGE id path [tileFeet]
VOXELMODEL id path
VOXSPRITE x y sector baseZ yawDeg scale modelId

PICKUP x y sector WEAPONNAME [amount]
PICKUP x y sector AMMO WEAPONNAME amount
```

- Colors are `0xRRGGBB`.
- `texId` on a `WALL`, or `floorTex`/`ceilTex` on a `SECTOR`, selects a
  texture: `0` brick, `1` panel, `2` stripes are the built-in procedural
  patterns, `-1` (or omitted) is a flat color, and any other id registered
  by an `IMAGE` line uses that image, tiled across `tileFeet` world feet
  (default 64). `IMAGE`/`VOXELMODEL` paths, like the map path itself, are
  resolved relative to the working directory the game is launched from.
- `SPRITE`'s optional trailing `imageTex` selects a registered image for an
  alpha-cutout billboard instead of a flat color box (pixels with alpha
  < 128 are skipped).
- `VOXELMODEL id path` loads a sparse voxel model (see `voxels/barrel.vxm`
  for an example): a text file of `DIM sizeX sizeY sizeZ`, `SCALE
  feetPerVoxel`, and `V x y z 0xRRGGBB` lines (unset voxels are empty).
  `VOXSPRITE` places an instance of it in the world with a yaw (degrees)
  and a scale multiplier on top of the model's own voxel size.
- `PICKUP` places a weapon or ammo item at a point; `WEAPONNAME` is one of
  the `WeaponType` enum names (`FOOT`, `PISTOL`, `SHOTGUN`, `CHAINGUN`,
  `RPG`, `DEVASTATOR`). The first form unlocks that
  weapon (plus `amount` ammo, or the weapon's own default if omitted); the
  `AMMO` form just tops up ammo for a weapon, owned or not.
- A `SECTOR`'s vertices are given implicitly by its `WALL` lines in order —
  wind them counter-clockwise from above, and the last wall must return to
  the first vertex.
- A shared wall between two sectors must be the exact same segment, listed
  by each sector in the opposite direction, with `portal` pointing at the
  other sector's id. All boundaries in the sample map are "full open"
  between rooms; to make a narrower doorway, split the wider room's wall
  into three collinear segments (solid, portal, solid) matching the
  narrower room's opening.

## Known limitations / what's not here

- No narrow doorways in the sample map (see above — easy to add).
- Voxel sprites are cube-splatted (projected and depth-sorted per voxel),
  not a true per-pixel voxel raycast, and aren't depth-tested against flat
  sprites pixel-for-pixel — only against walls. Fine for prop-sized models;
  large models cost more per frame since every occupied voxel is projected.
- No true room-over-room (two sectors overlapping in the *same* XY
  footprint at different heights) — sectors are choosing-by-nearest-floor
  when footprints overlap, which works for stacked-but-offset layouts but
  isn't a full Build-style "TROR" implementation.
- No enemies, so hitscan weapons and explosions have nothing to damage
  (sparks/flashes and screen shake still play). Only six weapons are
  implemented (Mighty Foot, Pistol, Shotgun, Chaingun Cannon, RPG,
  Devastator) — no Pipe Bomb, Shrinker, Tripbomb, or Freezethrower — and no
  sound at all; this engine has no audio subsystem yet.
