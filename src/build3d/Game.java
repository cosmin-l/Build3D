package build3d;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
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

    private final GameMap map;
    private final Player player = new Player();
    private final Renderer renderer = new Renderer(RENDER_W, RENDER_H, FOV_DEG);
    private final BufferedImage frame;
    private final int[] pixels;

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

            renderer.render(pixels, map, player);

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
            g2.drawImage(frame, 0, 0, WINDOW_W, WINDOW_H, null);

            g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
            g2.setColor(Color.GREEN);
            g2.drawString("FPS: " + fps, 10, WINDOW_H - 50);
            g2.setColor(Color.LIGHT_GRAY);
            String status = input.isCaptured()
                    ? "Mouse captured -- WASD move, Shift run, Esc release, Tab map"
                    : "Click window to capture mouse -- WASD/arrows move, Tab map";
            g2.drawString(status, 10, WINDOW_H - 30);
        } finally {
            g.dispose();
        }
        bs.show();
    }
}
