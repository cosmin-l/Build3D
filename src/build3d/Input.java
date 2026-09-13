package build3d;

import java.awt.AWTException;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Input implements KeyListener, MouseMotionListener, MouseListener {
    public static final double MOUSE_SENS = 0.0022;

    private final Set<Integer> keys = Collections.synchronizedSet(new HashSet<>());
    private final Component target;
    private Robot robot;
    private volatile boolean captured = false;
    private volatile boolean centering = false;

    public volatile double yawDelta = 0;
    public volatile double pitchDelta = 0;

    public Input(Component target) {
        this.target = target;
        try {
            robot = new Robot();
        } catch (AWTException e) {
            robot = null;
        }
    }

    public boolean isDown(int keyCode) { return keys.contains(keyCode); }
    public boolean isCaptured() { return captured; }

    public void setCaptured(boolean c) {
        captured = c;
        if (target.isDisplayable()) {
            target.setCursor(c ? blankCursor() : Cursor.getDefaultCursor());
        }
        if (c) centerMouse();
    }

    private Cursor blankCursor() {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        return java.awt.Toolkit.getDefaultToolkit().createCustomCursor(img, new Point(0, 0), "blank");
    }

    private void centerMouse() {
        if (robot == null || !target.isShowing()) return;
        centering = true;
        Point p = target.getLocationOnScreen();
        robot.mouseMove(p.x + target.getWidth() / 2, p.y + target.getHeight() / 2);
    }

    @Override public void mouseMoved(MouseEvent e) { handleMouse(e); }
    @Override public void mouseDragged(MouseEvent e) { handleMouse(e); }

    private void handleMouse(MouseEvent e) {
        if (!captured || robot == null) return;
        if (centering) {
            centering = false;
            return;
        }
        int cx = target.getWidth() / 2;
        int cy = target.getHeight() / 2;
        int dx = e.getX() - cx;
        int dy = e.getY() - cy;
        if (dx != 0 || dy != 0) {
            yawDelta += dx * MOUSE_SENS;
            pitchDelta += -dy * MOUSE_SENS;
            centerMouse();
        }
    }

    @Override public void mouseClicked(MouseEvent e) { if (!captured) setCaptured(true); }
    @Override public void mousePressed(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}

    @Override public void keyPressed(KeyEvent e) {
        keys.add(e.getKeyCode());
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) setCaptured(false);
    }
    @Override public void keyReleased(KeyEvent e) { keys.remove(e.getKeyCode()); }
    @Override public void keyTyped(KeyEvent e) {}
}
