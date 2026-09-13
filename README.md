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
- `Tab` — toggle a top-down minimap overlay

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
- Walls and floors/ceilings are flat-colored with cheap procedural
  "textures" (`Textures.java`: brick, panel, stripes — just math, no image
  assets) and distance-based darkening for a bit of depth cueing.
- Collision is deliberately simple: each tick, a candidate position is
  tested with a point-in-polygon check against every sector (`GameMap
  .findSector`), preferring continuity with the current sector and, when
  sector footprints overlap in XY, the one whose floor height is closest to
  where the player already is. Crossing into a neighboring sector is only
  allowed if the floor step is small (`Player.STEP_MAX`, an 8-Duke-unit-ish
  24) and the neighbor has headroom (`Player.HEIGHT`); a separate
  closest-point-on-segment push-out (`Game.resolveRadius`) keeps the player
  `Player.RADIUS` units from solid walls. There's no gravity/falling —
  stepping off a ledge just smoothly lowers the eye height.
- Sprites (`Sprite`) are flat-shaded camera-facing billboards, depth-tested
  per screen column against a `wallDepth[]` buffer recorded while drawing
  walls, then painted back-to-front.

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

SPRITE x y sector height color scale
```

- Colors are `0xRRGGBB`.
- `texId` for a `WALL` is a procedural texture: `0` brick, `1` panel, `2`
  stripes, `-1` (or omitted) flat color.
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
- Floors/ceilings are flat-shaded, not per-pixel textured (no floor-casting).
- No true room-over-room (two sectors overlapping in the *same* XY
  footprint at different heights) — sectors are choosing-by-nearest-floor
  when footprints overlap, which works for stacked-but-offset layouts but
  isn't a full Build-style "TROR" implementation.
- No enemies/weapons/game logic — this is the engine, not a game.
