package build3d;

/**
 * The Duke-Nukem-3D-style arsenal. Stats (fire rate, ammo caps, hold-to-auto)
 * mirror the original game's feel; look is original flat pixel-art sprites
 * drawn by {@link WeaponRenderer} since the real sprites/sounds aren't ours
 * to ship.
 */
public enum WeaponType {
    FOOT("Mighty Foot", Integer.MAX_VALUE, 0, 0.45, false, 0, 0),
    PISTOL("Pistol", 200, 1, 0.16, true, 0, 48),
    SHOTGUN("Shotgun", 50, 1, 0.65, true, 0, 8),
    CHAINGUN("Chaingun Cannon", 200, 1, 0.07, true, 0, 20),
    RPG("RPG", 50, 1, 0.8, true, 900, 5),
    DEVASTATOR("Devastator", 99, 1, 0.09, true, 760, 20);

    public final String label;
    public final int ammoMax;
    public final int ammoPerShot;
    /** Seconds between shots at full rate (chaingun/devastator ramp up to this). */
    public final double fireInterval;
    /** Whether holding the fire button keeps firing (vs. one shot per press -- here all weapons repeat on hold, matching Build-engine "use weapon" semantics; this flag is kept for weapons that additionally spin up). */
    public final boolean holdToAuto;
    /** World units/second for projectile weapons; 0 means hitscan/instant. */
    public final double projectileSpeed;
    public final int defaultPickupAmmo;

    WeaponType(String label, int ammoMax, int ammoPerShot, double fireInterval, boolean holdToAuto,
               double projectileSpeed, int defaultPickupAmmo) {
        this.label = label;
        this.ammoMax = ammoMax;
        this.ammoPerShot = ammoPerShot;
        this.fireInterval = fireInterval;
        this.holdToAuto = holdToAuto;
        this.projectileSpeed = projectileSpeed;
        this.defaultPickupAmmo = defaultPickupAmmo;
    }
}
