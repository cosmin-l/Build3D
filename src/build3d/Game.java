package build3d;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import javax.swing.JFrame;

public class Game {
    private static final int RENDER_W = 640;
    private static final int RENDER_H = 400;
    private static final int WINDOW_W = 1280;
    private static final int WINDOW_H = 800;
    private static final double FOV_DEG = 90;
    private static final double MOVE_SPEED = 160;   // world units / second
    private static final double RUN_MULT = 1.7;
    private static final double TURN_SPEED = 2.2;   // radians / second, keyboard turning
    private static final double GRAVITY = 800;      // world units / second^2
    private static final double JUMP_SPEED = 260;   // world units / second, initial upward velocity
    private static final int MAX_DECALS = 400;

    private final GameMap map;
    private final Player player = new Player();
    private final Renderer renderer = new Renderer(RENDER_W, RENDER_H, FOV_DEG);
    private final WeaponRenderer weaponRenderer = new WeaponRenderer();
    private final BufferedImage frame;
    private final int[] pixels;
    private final Random rng = new Random();
    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<Effect> effects = new ArrayList<>();
    /** Walls in the order bullet decals were added, so the oldest hole is removed first past the cap. */
    private final ArrayDeque<Wall> decalWalls = new ArrayDeque<>();
    private double screenShake = 0;

    private JFrame window;
    private Canvas canvas;
    private Input input;
    private volatile boolean running = false;
    private boolean tabWasDown = false;

    public Game(String mapPath) {
        try {
            map = MapLoader.load(mapPath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load map: " + mapPath, e);
        }
        player.applyMap(map);
        frame = new BufferedImage(RENDER_W, RENDER_H, BufferedImage.TYPE_INT_RGB);
        pixels = ((DataBufferInt) frame.getRaster().getDataBuffer()).getData();
    }

    public void start() {
        window = new JFrame("Build3D");
        canvas = new Canvas();
        canvas.setPreferredSize(new Dimension(WINDOW_W, WINDOW_H));
        canvas.setIgnoreRepaint(true);
        window.add(canvas);
        window.pack();
        window.setResizable(false);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setLocationRelativeTo(null);
        window.setVisible(true);

        input = new Input(canvas);
        canvas.addKeyListener(input);
        canvas.addMouseMotionListener(input);
        canvas.addMouseListener(input);
        canvas.addMouseWheelListener(input);
        canvas.setFocusable(true);
        canvas.requestFocus();

        canvas.createBufferStrategy(2);
        running = true;
        Thread loop = new Thread(this::loop, "game-loop");
        loop.setDaemon(true);
        loop.start();
    }

    private void loop() {
        long lastNs = System.nanoTime();
        double accumulator = 0;
        final double dt = 1.0 / 60.0;
        int frames = 0;
        long fpsTimer = System.nanoTime();
        int fps = 0;

        while (running) {
            long now = System.nanoTime();
            double elapsed = (now - lastNs) / 1_000_000_000.0;
            lastNs = now;
            accumulator += Math.min(elapsed, 0.25);

            while (accumulator >= dt) {
                update(dt);
                accumulator -= dt;
            }

            renderer.render(pixels, map, player, buildDynamicSprites());

            frames++;
            if (now - fpsTimer >= 1_000_000_000L) {
                fps = frames;
                frames = 0;
                fpsTimer = now;
            }
            present(fps);

            try { Thread.sleep(1); } catch (InterruptedException ignored) {}
        }
    }

    private void update(double dt) {
        // Mouse look
        player.angle += input.yawDelta;
        player.pitch = clampPitch(player.pitch + input.pitchDelta);
        input.yawDelta = 0;
        input.pitchDelta = 0;

        // Keyboard turning / looking (works without mouse capture too)
        if (input.isDown(KeyEvent.VK_LEFT)) player.angle -= TURN_SPEED * dt;
        if (input.isDown(KeyEvent.VK_RIGHT)) player.angle += TURN_SPEED * dt;
        if (input.isDown(KeyEvent.VK_UP)) player.pitch = clampPitch(player.pitch + 1.2 * dt);
        if (input.isDown(KeyEvent.VK_DOWN)) player.pitch = clampPitch(player.pitch - 1.2 * dt);

        boolean tabDown = input.isDown(KeyEvent.VK_TAB);
        if (tabDown && !tabWasDown) renderer.minimapOn = !renderer.minimapOn;
        tabWasDown = tabDown;

        double speed = MOVE_SPEED * (input.isDown(KeyEvent.VK_SHIFT) ? RUN_MULT : 1.0) * dt;
        double fx = player.forwardX(), fy = player.forwardY();
        double rx = player.rightX(), ry = player.rightY();
        double mx = 0, my = 0;
        if (input.isDown(KeyEvent.VK_W)) { mx += fx; my += fy; }
        if (input.isDown(KeyEvent.VK_S)) { mx -= fx; my -= fy; }
        if (input.isDown(KeyEvent.VK_D)) { mx += rx; my += ry; }
        if (input.isDown(KeyEvent.VK_A)) { mx -= rx; my -= ry; }
        double len = Math.hypot(mx, my);
        if (len > 1e-6) {
            mx = mx / len * speed;
            my = my / len * speed;
            move(mx, my);
        }

        Sector cur = map.sectors.get(player.sector);
        double targetEyeZ = cur.floorZ + player.eyeHeightOffset;

        if (input.isDown(KeyEvent.VK_SPACE) && player.velZ == 0 && player.eyeZ <= targetEyeZ + 0.5) {
            player.velZ = JUMP_SPEED;
        }

        if (player.velZ != 0 || player.eyeZ > targetEyeZ + 0.5) {
            player.velZ -= GRAVITY * dt;
            player.eyeZ += player.velZ * dt;
            double maxEyeZ = cur.ceilZ - (Player.HEIGHT - player.eyeHeightOffset);
            if (player.eyeZ > maxEyeZ) {
                player.eyeZ = maxEyeZ;
                if (player.velZ > 0) player.velZ = 0;
            }
            if (player.eyeZ <= targetEyeZ) {
                player.eyeZ = targetEyeZ;
                player.velZ = 0;
            }
        } else {
            player.eyeZ += (targetEyeZ - player.eyeZ) * Math.min(1.0, dt * 10.0);
        }

        boolean moving = len > 1e-6;
        double bobFrac = moving ? (input.isDown(KeyEvent.VK_SHIFT) ? 1.4 : 1.0) : 0.0;
        handleWeaponInput(dt);
        player.weapons.update(dt, moving, bobFrac);
        updateProjectiles(dt);
        updateEffects(dt);
        updatePickups();
        screenShake *= Math.exp(-dt * 6);
    }

    private void handleWeaponInput(double dt) {
        WeaponSystem ws = player.weapons;
        WeaponType[] order = WeaponType.values();
        int[] numKeys = { KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3, KeyEvent.VK_4,
                KeyEvent.VK_5, KeyEvent.VK_6 };
        for (int i = 0; i < numKeys.length && i < order.length; i++) {
            if (input.isDown(numKeys[i]) && ws.owned.contains(order[i])) ws.startSwitch(order[i]);
        }

        int wheel = input.consumeWheel();
        if (wheel > 0) ws.cycle(1);
        else if (wheel < 0) ws.cycle(-1);

        boolean leftHeld = input.isCaptured() && input.isMouseDown(MouseEvent.BUTTON1);

        boolean continuous = ws.current == WeaponType.CHAINGUN || ws.current == WeaponType.DEVASTATOR;
        if (continuous && leftHeld && !ws.isSwitching()) {
            ws.chainSpin = Math.min(1, ws.chainSpin + dt / 0.35);
        } else {
            ws.chainSpin = Math.max(0, ws.chainSpin - dt / 0.5);
        }

        if (leftHeld && ws.canFire()) fireWeapon(ws.current);
    }

    private void fireWeapon(WeaponType wt) {
        WeaponSystem ws = player.weapons;
        ws.consumeAmmo(wt.ammoPerShot);
        ws.recoil = 1.0;
        double interval = wt.fireInterval;

        switch (wt) {
            case FOOT:
                spawnMeleeSwipe();
                break;
            case PISTOL:
                ws.pistolClipCount++;
                fireHitscan(5.5);
                if (ws.pistolClipCount >= 12) {
                    ws.pistolClipCount = 0;
                    ws.reloadT = 0.55;
                }
                break;
            case SHOTGUN:
                for (int i = 0; i < 7; i++) fireHitscan(10.0);
                ws.pumpT = 0.35;
                break;
            case CHAINGUN:
                fireHitscan(4.5);
                ws.altBarrel = !ws.altBarrel;
                interval = lerp(wt.fireInterval * 2.4, wt.fireInterval, ws.chainSpin);
                break;
            case RPG:
                spawnProjectile(Projectile.Kind.ROCKET, wt.projectileSpeed);
                break;
            case DEVASTATOR:
                spawnProjectile(Projectile.Kind.DEVASTATOR_ROCKET, wt.projectileSpeed);
                ws.altBarrel = !ws.altBarrel;
                interval = lerp(wt.fireInterval * 2.0, wt.fireInterval, ws.chainSpin);
                break;
        }

        ws.fireCooldown = interval;
        if (wt != WeaponType.FOOT && ws.ammo(wt) <= 0) autoSwitchAfterEmpty();
    }

    private void autoSwitchAfterEmpty() {
        WeaponType[] priority = { WeaponType.DEVASTATOR, WeaponType.RPG,
                WeaponType.CHAINGUN, WeaponType.SHOTGUN, WeaponType.PISTOL, WeaponType.FOOT };
        for (WeaponType w : priority) {
            if (player.weapons.owned.contains(w) && (w == WeaponType.FOOT || player.weapons.ammo(w) > 0)) {
                player.weapons.startSwitch(w);
                return;
            }
        }
    }

    private void fireHitscan(double spreadDeg) {
        double maxSpread = Math.toRadians(spreadDeg);
        double ang = player.angle + maxSpread * (rng.nextDouble() * 2 - 1);
        GameMap.RaycastHit hit = map.raycast(player.x, player.y, player.sector, ang, 1400);
        double dirX = Math.sin(ang), dirY = Math.cos(ang);

        // Exact wall the ray left the map through (raycast only gives a point within one step of it).
        Wall hitWall = null;
        double bestT = Double.POSITIVE_INFINITY;
        if (hit.sector >= 0 && hit.sector < map.sectors.size()) {
            for (Wall w : map.sectors.get(hit.sector).walls) {
                if (w.portal >= 0) continue;
                double t = rayWallT(player.x, player.y, dirX, dirY, w);
                if (t > 0 && Math.abs(t - hit.dist) < Math.abs(bestT - hit.dist)) {
                    bestT = t;
                    hitWall = w;
                }
            }
        }
        double dist = hitWall != null ? bestT : hit.dist;
        double ix = player.x + dirX * dist, iy = player.y + dirY * dist;

        // The crosshair ray climbs by `pitch` world units per unit of distance (the
        // Build-style y-shear); vertical spread is a bit tighter than horizontal.
        double z = player.eyeZ + dist * (player.pitch + Math.tan(maxSpread * 0.6 * (rng.nextDouble() * 2 - 1)));
        Sector sec = map.sectors.get(hit.sector);
        boolean onWall = hitWall != null && z > sec.floorZ + 1 && z < sec.ceilZ - 1;
        if (onWall) addDecal(hitWall, ix, iy, z);

        double sz = Math.max(sec.floorZ + 2, Math.min(sec.ceilZ - 2, z));
        // Pull the spark back off the surface so it isn't clipped by the wall it hit.
        effects.add(new Effect(ix - dirX * 2, iy - dirY * 2, sz - 3, hit.sector, 0.18, Effect.Kind.SPARK));
    }

    /** Distance along the ray (ox,oy)+t*(dx,dy) where it crosses wall w, or -1 if it misses. */
    private static double rayWallT(double ox, double oy, double dx, double dy, Wall w) {
        double ex = w.x2 - w.x1, ey = w.y2 - w.y1;
        double denom = dx * ey - dy * ex;
        if (Math.abs(denom) < 1e-9) return -1;
        double qx = w.x1 - ox, qy = w.y1 - oy;
        double t = (qx * ey - qy * ex) / denom;
        double s = (qx * dy - qy * dx) / denom;
        return (s >= 0 && s <= 1) ? t : -1;
    }

    private void addDecal(Wall w, double ix, double iy, double z) {
        double u = Math.hypot(ix - w.x1, iy - w.y1);
        w.decals.add(new Decal(u, z, rng.nextDouble() * Math.PI * 2));
        decalWalls.addLast(w);
        if (decalWalls.size() > MAX_DECALS) decalWalls.removeFirst().decals.remove(0);
    }

    private void spawnProjectile(Projectile.Kind kind, double speed) {
        double dx = player.forwardX(), dy = player.forwardY();
        double z = player.eyeZ - 8;
        projectiles.add(new Projectile(player.x + dx * 20, player.y + dy * 20, z,
                dx * speed, dy * speed, 0, player.sector, kind));
    }

    private void spawnMeleeSwipe() {
        double dx = player.forwardX(), dy = player.forwardY();
        effects.add(new Effect(player.x + dx * 26, player.y + dy * 26, player.eyeZ - 10, player.sector, 0.15, Effect.Kind.SPARK));
    }

    private void explode(double x, double y, double z, int sector) {
        effects.add(new Effect(x, y, z, sector, 0.5, Effect.Kind.EXPLOSION));
        effects.add(new Effect(x, y, z, sector, 1.0, Effect.Kind.SMOKE));
        double d = Math.hypot(x - player.x, y - player.y);
        if (d < 260) screenShake = Math.max(screenShake, 1.0 - d / 260);
    }

    private void updateProjectiles(double dt) {
        Iterator<Projectile> it = projectiles.iterator();
        while (it.hasNext()) {
            Projectile p = it.next();
            p.life -= dt;

            double nx = p.x + p.vx * dt, ny = p.y + p.vy * dt, nz = p.z + p.vz * dt;
            Sector sec = map.sectors.get(p.sector);
            int newSector = map.findSector(nx, ny, p.sector, sec.floorZ);
            boolean hitWall = newSector < 0;
            Sector checkSec = hitWall ? sec : map.sectors.get(newSector);
            boolean hitFloor = nz <= checkSec.floorZ + 2;
            boolean hitCeil = nz >= checkSec.ceilZ - 2;

            if (hitWall || hitFloor || hitCeil || p.life <= 0) {
                explode(p.x, p.y, p.z, p.sector);
                it.remove();
                continue;
            }
            p.x = nx; p.y = ny; p.z = nz; p.sector = newSector;
        }
    }

    private void updateEffects(double dt) {
        Iterator<Effect> it = effects.iterator();
        while (it.hasNext()) {
            Effect e = it.next();
            e.age += dt;
            if (e.age >= e.ttl) it.remove();
        }
    }

    private void updatePickups() {
        for (Pickup pk : map.pickups) {
            if (pk.taken || pk.sector != player.sector) continue;
            double d = Math.hypot(pk.x - player.x, pk.y - player.y);
            if (d < 28) {
                pk.taken = true;
                if (pk.kind == Pickup.Kind.WEAPON) player.weapons.pickupWeapon(pk.weapon, pk.amount);
                else player.weapons.pickupAmmo(pk.weapon, pk.amount);
            }
        }
    }

    private List<Sprite> buildDynamicSprites() {
        List<Sprite> list = new ArrayList<>();
        for (Projectile p : projectiles) {
            int color = p.kind == Projectile.Kind.DEVASTATOR_ROCKET ? 0xd0d0d0 : 0x4a6b2a;
            list.add(new Sprite(p.x, p.y, p.sector, p.z - 4, color, 0.45));
        }
        for (Effect e : effects) {
            double t = e.progress();
            int color;
            double scale;
            switch (e.kind) {
                case EXPLOSION:
                    color = lerpColor(0xffffcc, 0xff5500, t);
                    scale = lerp(0.3, 1.6, t);
                    break;
                case SMOKE:
                    color = Textures.darken(0x555555, 1.0 - t * 0.6);
                    scale = lerp(0.6, 1.4, t);
                    break;
                default: // SPARK
                    color = lerpColor(0xffffee, 0xff8800, t);
                    scale = lerp(0.22, 0.05, t);
                    break;
            }
            list.add(new Sprite(e.x, e.y, e.sector, e.z - 4, color, Math.max(0.03, scale)));
        }
        return list;
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    private static int lerpColor(int a, int b, double t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t), g = (int) (ag + (bg - ag) * t), bl = (int) (ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private void move(double mx, double my) {
        Sector curSector = map.sectors.get(player.sector);
        double newX = player.x + mx, newY = player.y + my;

        int target = map.findSector(newX, newY, player.sector, curSector.floorZ);
        if (target >= 0 && passable(player.sector, target)) {
            player.x = newX;
            player.y = newY;
            player.sector = target;
        } else {
            // slide along walls: try each axis independently
            int tx = map.findSector(player.x + mx, player.y, player.sector, curSector.floorZ);
            if (tx >= 0 && passable(player.sector, tx)) {
                player.x += mx;
                player.sector = tx;
            }
            curSector = map.sectors.get(player.sector);
            int ty = map.findSector(player.x, player.y + my, player.sector, curSector.floorZ);
            if (ty >= 0 && passable(player.sector, ty)) {
                player.y += my;
                player.sector = ty;
            }
        }
        resolveRadius();
    }

    private boolean passable(int fromIdx, int toIdx) {
        if (fromIdx == toIdx) return true;
        Sector from = map.sectors.get(fromIdx);
        Sector to = map.sectors.get(toIdx);
        double step = to.floorZ - from.floorZ;
        double clearance = to.ceilZ - to.floorZ;
        return step <= Player.STEP_MAX && clearance >= Player.HEIGHT;
    }

    private void resolveRadius() {
        Sector sec = map.sectors.get(player.sector);
        for (int iter = 0; iter < 2; iter++) {
            for (Wall w : sec.walls) {
                if (w.portal >= 0) continue; // only push off solid walls
                double px = closestPointX(w, player.x, player.y);
                double py = closestPointY(w, player.x, player.y);
                double dx = player.x - px, dy = player.y - py;
                double dist = Math.hypot(dx, dy);
                if (dist < Player.RADIUS && dist > 1e-6) {
                    double push = (Player.RADIUS - dist) / dist;
                    player.x += dx * push;
                    player.y += dy * push;
                }
            }
        }
    }

    private double closestPointX(Wall w, double px, double py) {
        return closest(w, px, py)[0];
    }

    private double closestPointY(Wall w, double px, double py) {
        return closest(w, px, py)[1];
    }

    private double[] closest(Wall w, double px, double py) {
        double dx = w.x2 - w.x1, dy = w.y2 - w.y1;
        double len2 = dx * dx + dy * dy;
        double t = len2 < 1e-9 ? 0 : ((px - w.x1) * dx + (py - w.y1) * dy) / len2;
        t = Math.max(0, Math.min(1, t));
        return new double[] { w.x1 + dx * t, w.y1 + dy * t };
    }

    private double clampPitch(double p) {
        return Math.max(-Player.PITCH_LIMIT, Math.min(Player.PITCH_LIMIT, p));
    }

    private void present(int fps) {
        BufferStrategy bs = canvas.getBufferStrategy();
        Graphics g = bs.getDrawGraphics();
        try {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            if (screenShake > 0.01) {
                double mag = screenShake * 10;
                g2.translate((rng.nextDouble() * 2 - 1) * mag, (rng.nextDouble() * 2 - 1) * mag);
            }
            g2.drawImage(frame, 0, 0, WINDOW_W, WINDOW_H, null);

            weaponRenderer.drawViewmodel(g2, WINDOW_W, WINDOW_H, player.weapons);
            weaponRenderer.drawCrosshair(g2, WINDOW_W, WINDOW_H);
            weaponRenderer.drawHud(g2, WINDOW_W, WINDOW_H, player.weapons);

            g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
            g2.setColor(Color.GREEN);
            g2.drawString("FPS: " + fps, 10, WINDOW_H - 50);
            g2.setColor(Color.LIGHT_GRAY);
            String status = input.isCaptured()
                    ? "Mouse captured -- WASD move, Shift run, LMB fire, 1-6/wheel switch, Esc release, Tab map"
                    : "Click window to capture mouse -- WASD/arrows move, Tab map";
            g2.drawString(status, 10, WINDOW_H - 30);
        } finally {
            g.dispose();
        }
        bs.show();
    }
}
