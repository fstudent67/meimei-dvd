package meimei;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Encarrega-se de carregar o PNG (o teu "snow leopard mei mei") e de o preparar
 * para o screensaver: redimensiona com qualidade e sabe pintar-lhe cores.
 */
public final class Png {

    private Png() {
    }

    /**
     * Procura o PNG, por ordem:
     *   1) caminho dado com --png
     *   2) meimei.png na pasta actual
     *   3) meimei.png ao lado do .jar
     * Devolve null se nao encontrar nenhum.
     */
    public static File find() {
        List<String> paths = new ArrayList<>();
        String cli = System.getProperty("meimei.png");
        if (cli != null && !cli.isBlank()) {
            paths.add(cli);
        }
        paths.add("meimei.png");
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            File dir = new File(entry).getAbsoluteFile().getParentFile();
            if (dir != null) {
                paths.add(new File(dir, "meimei.png").getPath());
            }
        }
        for (String p : paths) {
            File f = new File(p);
            if (f.isFile() && f.length() > 0) {
                return f.getAbsoluteFile();
            }
        }
        return null;
    }

    /** Le o PNG. */
    public static BufferedImage read(File file) throws java.io.IOException {
        BufferedImage img = javax.imageio.ImageIO.read(file);
        if (img == null) {
            throw new java.io.IOException("nao consegui ler " + file);
        }
        return img;
    }

    /** Redimensiona mantendo a proporcao para caber em max x max. */
    public static BufferedImage scale(BufferedImage src, int max) {
        int w = src.getWidth();
        int h = src.getHeight();
        double k = (double) max / Math.max(w, h);
        int nw = Math.max(1, (int) Math.round(w * k));
        int nh = Math.max(1, (int) Math.round(h * k));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    /** Pinta as zonas claras do PNG com a cor dada (mantem o alpha / contorno). */    public static BufferedImage tint(BufferedImage src, Color c) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) {
                    continue;
                }
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                double lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
                int nr = (int) Math.min(255, c.getRed() * (0.35 + 0.85 * lum));
                int ng = (int) Math.min(255, c.getGreen() * (0.35 + 0.85 * lum));
                int nb = (int) Math.min(255, c.getBlue() * (0.35 + 0.85 * lum));
                out.setRGB(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
            }
        }
        return out;
    }
}
