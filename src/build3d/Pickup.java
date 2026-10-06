package build3d;

/** A world item: a weapon to unlock (plus starting ammo) or a bare ammo refill. */
public class Pickup {
    public enum Kind { WEAPON, AMMO }

    public final double x, y;
    public final int sector;
    public final Kind kind;
    public final WeaponType weapon;
    public final int amount;
    public boolean taken = false;

    public Pickup(double x, double y, int sector, Kind kind, WeaponType weapon, int amount) {
        this.x = x;
        this.y = y;
        this.sector = sector;
        this.kind = kind;
        this.weapon = weapon;
        this.amount = amount;
    }
}
