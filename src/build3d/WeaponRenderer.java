package build3d;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Draws the first-person weapon viewmodel and ammo/weapon HUD. Weapon art is
 * real pixel-art sprite frames (idle / fire / pump-or-reload / alternating
 * muzzle flash), sourced from the Freedoom project's BSD-licensed Doom
 * sprite replacements (see textures/weapons/FREEDOOM_LICENSE.txt) -- not
 * procedurally drawn -- so they actually look like the genre, not an
 * abstract shape cluster. Frames are blitted with nearest-neighbor scaling
 * at the sprites' native resolution scaled up the same way Doom's own
 * 320x200 assets were, so they read as chunky period-correct pixel art
 * rather than a smoothly filtered photo.
 */
public class WeaponRenderer {
    private static final String DIR = "textures/weapons/";
    /** Doom's native vertical resolution -- scaling sprites by (windowHeight / this) reproduces their authentic on-screen size. */
    private static final double DOOM_SCALE_BASIS = 200.0;

    private final Map<String, BufferedImage> imageCache = new HashMap<>();

    private BufferedImage load(String name) {
        return imageCache.computeIfAbsent(name, n -> {
            try {
                return ImageIO.read(new File(DIR + n));
            } catch (IOException e) {
                throw new RuntimeException("Missing weapon sprite: " + DIR + n, e);
            }
        });
    }

    private BufferedImage baseImage(WeaponType wt, int frame) {
        switch (wt) {
            case FOOT: return frame == 1 ? load("foot_punch.png") : load("foot_idle.png");
            case PISTOL:
                if (frame == 1) return load("pistol_fire.png");
                if (frame == 2) return load("pistol_recover.png");
                return load("pistol_idle.png");
            case SHOTGUN:
                if (frame == 1) return load("shotgun_fire.png");
                if (frame == 2) return load("shotgun_pump.png");
                return load("shotgun_idle.png");
            case CHAINGUN:
                if (frame == 1) return load("chaingun_fireA.png");
                if (frame == 2) return load("chaingun_fireB.png");
                return load("chaingun_idle.png");
            case RPG: return frame == 1 ? load("rpg_fire.png") : load("rpg_idle.png");
            case DEVASTATOR:
                if (frame == 1) return load("devastator_fireA.png");
                if (frame == 2) return load("devastator_fireB.png");
                return load("devastator_idle.png");
            default: return null;
        }
    }

    private BufferedImage flashImage(WeaponType wt, int frame) {
        switch (wt) {
            case PISTOL: return frame == 1 ? load("pistol_flash.png") : null;
            case SHOTGUN: return frame == 1 ? load("shotgun_flash.png") : null;
            case CHAINGUN:
                if (frame == 1) return load("chaingun_fireA_flash.png");
                if (frame == 2) return load("chaingun_fireB_flash.png");
                return null;
            case RPG: return frame == 1 ? load("rpg_flash.png") : null;
            default: return null;
        }
    }

    /** Fraction of the gun sprite's height, measured up from its bottom, where the flash sprite's bottom should sit. */
    private double flashAnchorFrac(WeaponType wt) {
        switch (wt) {
            case SHOTGUN: return 0.95;
            case CHAINGUN: return 0.98;
            case RPG: return 0.9;
            default: return 0.92; // PISTOL
        }
    }

    /** Picks the discrete animation frame from the weapon's existing state timers. */
    private int frameIndexFor(WeaponSystem ws) {
        boolean firing = ws.recoil > 0.4;
        switch (ws.current) {
            case FOOT: return firing ? 1 : 0;
            case PISTOL: return ws.reloadT > 0 ? 2 : (firing ? 1 : 0);
            case SHOTGUN: return firing ? 1 : (ws.pumpT > 0 ? 2 : 0);
            case CHAINGUN: return !firing ? 0 : (ws.altBarrel ? 2 : 1);
            case RPG: return firing ? 1 : 0;
            case DEVASTATOR: return !firing ? 0 : (ws.altBarrel ? 2 : 1);
            default: return 0;
        }
    }

    public void drawViewmodel(Graphics2D g2, int w, int h, WeaponSystem ws) {
        double scale = h / DOOM_SCALE_BASIS;
        double bx = Math.sin(ws.bobPhase) * 10;
        double by = Math.abs(Math.sin(ws.bobPhase * 2)) * 8;
        double recoilY = -ws.recoil * 14;
        double downY = ws.switchT * h * 0.75;

        double anchorX = w * 0.52;
        double anchorY = h * 1.0;

        int frame = frameIndexFor(ws);

        AffineTransform old = g2.getTransform();
        Object oldInterp = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        Object oldAA = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        g2.translate(anchorX + bx, anchorY + by + recoilY + downY);
        g2.scale(scale, scale);

        BufferedImage base = baseImage(ws.current, frame);
        drawBottomCenter(g2, base, 0, 0);
        BufferedImage flash = flashImage(ws.current, frame);
        if (flash != null) {
            double flashBottomY = -base.getHeight() * flashAnchorFrac(ws.current);
            drawBottomCenter(g2, flash, 0, flashBottomY);
        }

        g2.setTransform(old);
        if (oldInterp != null) g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterp);
        if (oldAA != null) g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
    }

    private void drawBottomCenter(Graphics2D g2, BufferedImage img, double cx, double bottomY) {
        int iw = img.getWidth(), ih = img.getHeight();
        g2.drawImage(img, (int) Math.round(cx - iw / 2.0), (int) Math.round(bottomY - ih), iw, ih, null);
    }

    /** Classic "+" crosshair at screen center, with a dark outline so it stays visible on any background. */
    public void drawCrosshair(Graphics2D g2, int w, int h) {
        int cx = w / 2, cy = h / 2;
        int gap = 3, len = 7, t = 2;
        int[][] arms = {
                { cx - gap - len, cy - t / 2, len, t },  // left
                { cx + gap + 1,   cy - t / 2, len, t },  // right
                { cx - t / 2, cy - gap - len, t, len },  // top
                { cx - t / 2, cy + gap + 1,   t, len },  // bottom
        };
        g2.setColor(new Color(0, 0, 0, 170));
        for (int[] a : arms) g2.fillRect(a[0] - 1, a[1] - 1, a[2] + 2, a[3] + 2);
        g2.setColor(new Color(120, 255, 120, 230));
        for (int[] a : arms) g2.fillRect(a[0], a[1], a[2], a[3]);
    }

    public void drawHud(Graphics2D g2, int w, int h, WeaponSystem ws) {
        Object oldHint = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(new Color(0, 0, 0, 140));
        g2.fillRect(w - 220, h - 70, 210, 60);
        g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));
        g2.setColor(Color.YELLOW);
        g2.drawString(ws.current.label.toUpperCase(), w - 210, h - 44);
        g2.setColor(Color.WHITE);
        String ammoStr = ws.current == WeaponType.FOOT ? "---" : String.valueOf(ws.ammo(ws.current));
        g2.drawString("AMMO " + ammoStr, w - 210, h - 18);

        WeaponType[] order = WeaponType.values();
        int boxW = 26, gap = 4;
        int totalW = order.length * (boxW + gap);
        int startX = w - totalW - 20;
        int y = h - 100;
        g2.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
        for (int i = 0; i < order.length; i++) {
            WeaponType wt = order[i];
            int x = startX + i * (boxW + gap);
            boolean owned = ws.owned.contains(wt);
            boolean cur = wt == ws.current;
            g2.setColor(cur ? new Color(255, 210, 60) : (owned ? new Color(70, 70, 70, 200) : new Color(30, 30, 30, 160)));
            g2.fillRect(x, y, boxW, boxW);
            g2.setColor(owned ? Color.WHITE : new Color(90, 90, 90));
            g2.drawRect(x, y, boxW, boxW);
            g2.drawString(String.valueOf(i + 1), x + 8, y + 19);
        }

        if (ws.messageT > 0) {
            float alpha = (float) Math.min(1.0, ws.messageT / 0.4);
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
            g2.setColor(new Color(255, 230, 90, (int) (alpha * 255)));
            int tw = g2.getFontMetrics().stringWidth(ws.message);
            g2.drawString(ws.message, w / 2 - tw / 2, (int) (h * 0.22));
        }

        if (oldHint != null) g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldHint);
    }
}
