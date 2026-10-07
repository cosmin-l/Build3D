package build3d;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-player weapon state: what's owned, ammo counts, which weapon is up,
 * and the little animation timers (switch lower/raise, recoil, muzzle flash,
 * chaingun spin-up, shotgun pump, pistol reload, pickup message) that drive
 * {@link WeaponRenderer}. Game.java owns all the "what happens when you fire"
 * logic (hitscans, projectiles); this class only tracks state.
 */
public class WeaponSystem {
    public WeaponType current = WeaponType.PISTOL;
    public final Map<WeaponType, Integer> ammo = new EnumMap<>(WeaponType.class);
    public final Set<WeaponType> owned = EnumSet.allOf(WeaponType.class);

    /** Non-null while lowering toward a switch; becomes {@code current} once fully down. */
    private WeaponType switchTarget = null;
    private boolean raising = false;
    /** 0 = fully up/idle, 1 = fully lowered off-screen. */
    public double switchT = 0;

    public double fireCooldown = 0;
    public double recoil = 0;
    public double chainSpin = 0;
    public double pumpT = 0;
    public double reloadT = 0;
    public int pistolClipCount = 0;
    public double bobPhase = 0;
    public boolean altBarrel = false;

    public String message = "";
    public double messageT = 0;

    public WeaponSystem() {
        // Start with the full arsenal, fully stocked.
        for (WeaponType w : WeaponType.values()) ammo.put(w, w.ammoMax);
    }

    public int ammo(WeaponType w) { return ammo.getOrDefault(w, 0); }

    public boolean isSwitching() { return switchTarget != null || raising; }

    public boolean canFire() {
        return !isSwitching() && fireCooldown <= 0 && reloadT <= 0
                && (current == WeaponType.FOOT || ammo(current) > 0);
    }

    public void startSwitch(WeaponType to) {
        if (to == current || !owned.contains(to) || switchTarget == to) return;
        switchTarget = to;
        raising = false;
    }

    public void cycle(int dir) {
        WeaponType[] vals = WeaponType.values();
        int i = current.ordinal();
        for (int n = 0; n < vals.length; n++) {
            i = (i + dir + vals.length) % vals.length;
            WeaponType cand = vals[i];
            if (owned.contains(cand)) { startSwitch(cand); return; }
        }
    }

    public void consumeAmmo(int n) {
        if (current == WeaponType.FOOT) return;
        ammo.put(current, Math.max(0, ammo(current) - n));
    }

    public void pickupWeapon(WeaponType w, int amount) {
        boolean first = !owned.contains(w);
        owned.add(w);
        ammo.put(w, Math.min(w.ammoMax, ammo(w) + amount));
        showMessage("Got the " + w.label + "!");
        if (first) startSwitch(w);
    }

    public void pickupAmmo(WeaponType w, int amount) {
        ammo.put(w, Math.min(w.ammoMax, ammo(w) + amount));
        showMessage("Got " + w.label + " ammo!");
    }

    public void showMessage(String s) {
        message = s;
        messageT = 2.2;
    }

    public void update(double dt, boolean moving, double bobFrac) {
        if (fireCooldown > 0) fireCooldown -= dt;
        if (reloadT > 0) reloadT -= dt;
        if (pumpT > 0) pumpT -= dt;
        if (messageT > 0) messageT -= dt;
        recoil *= Math.exp(-dt * 12);
        if (recoil < 0.002) recoil = 0;

        if (switchTarget != null) {
            switchT = Math.min(1, switchT + dt / 0.12);
            if (switchT >= 1) {
                current = switchTarget;
                switchTarget = null;
                raising = true;
                pistolClipCount = 0;
                chainSpin = 0;
            }
        } else if (raising) {
            switchT = Math.max(0, switchT - dt / 0.14);
            if (switchT <= 0) raising = false;
        }

        bobPhase += dt * (moving ? 7.0 * Math.max(0.4, bobFrac) : 1.4);
    }
}
