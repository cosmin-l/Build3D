package build3d;

import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        String mapPath = args.length > 0 ? args[0] : "maps/sample.map";
        SwingUtilities.invokeLater(() -> new Game(mapPath).start());
    }
}
