package build3d;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/** An image-file-backed texture. Wall/flat sampling tiles it across world feet; sprite sampling addresses it by normalized [0,1) UV. */
public class ImageTexture {
    public final int width, height;
    private final int[] argb;
    /** World feet spanned by one full tile of the image, for wall/floor/ceiling sampling. */
    public final double tileFeet;

    public ImageTexture(BufferedImage img, double tileFeet) {
        this.width = img.getWidth();
        this.height = img.getHeight();
        this.tileFeet = tileFeet;
        this.argb = img.getRGB(0, 0, width, height, null, 0, width);
    }

    public static ImageTexture load(String path, double tileFeet) throws IOException {
        BufferedImage img = ImageIO.read(new File(path));
        if (img == null) throw new IOException("Not a readable image: " + path);
        return new ImageTexture(img, tileFeet);
    }

    /** Sample by world-space (u, v) feet, tiling/wrapping seamlessly; alpha ignored by the caller. */
    public int sampleARGB(double u, double v) {
        int px = wrapIndex(u / tileFeet, width);
        int py = wrapIndex(v / tileFeet, height);
        return argb[py * width + px];
    }

    /** Sample by normalized [0,1) UV across the whole image, clamped at the edges (for sprite billboards). */
    public int sampleNormalizedARGB(double u, double v) {
        int px = clampIndex(u, width);
        int py = clampIndex(v, height);
        return argb[py * width + px];
    }

    private static int wrapIndex(double frac, int size) {
        double f = frac - Math.floor(frac);
        int i = (int) (f * size);
        return i >= size ? size - 1 : i;
    }

    private static int clampIndex(double frac, int size) {
        int i = (int) (frac * size);
        if (i < 0) return 0;
        return i >= size ? size - 1 : i;
    }
}
