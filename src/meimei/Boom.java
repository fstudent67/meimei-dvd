package meimei;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Random;

/**
 * Explosao. Ha duas, propositadamente diferentes:
 *
 *   CANTO   - rajada curta: clarao, uma onda e particulas a cair. E o premio
 *             pequeno por ter acertado num canto.
 *   CENTRAL - muito maior e mais longa: clarao branco, tres ondas de choque a
 *             velocidades diferentes, estrela de raios e chuveiro de fagulhas.
 *             E a que anuncia que nasceram mais mei meis.
 *
 * Desenhada no mesmo espaco que os bouncers, vale para o modo janela e para a
 * overlay transparente.
 */
public final class Boom {

    /** Que tipo de explosao e. */
    public enum Kind {
        CORNER, MIDDLE
    }

    private static final int PARTICLES = 46;
    private static final int SPARKS = 90;
    private static final int RAYS = 22;

    private final Kind kind;
    private final double x;
    private final double y;
    private final double hue;

    /** Vida util, em segundos. */
    private final double life;

    /** Raio ate onde a onda de choque chega. */
    private final double reach;

    private final double[] px;
    private final double[] py;
    private final double[] vx;
    private final double[] vy;
    private final double[] size;
    private final double[] tint;

    /** Angulos e comprimento dos raios (so na central). */
    private final double[] rayA;
    private final double[] rayLen;

    private double age;

    public Boom(double x, double y, double hue, Kind kind, double reach) {
        this.x = x;
        this.y = y;
        this.hue = hue;
        this.kind = kind;
        this.reach = reach;
        this.life = kind == Kind.MIDDLE ? 1.9 : 1.0;

        int n = kind == Kind.MIDDLE ? SPARKS : PARTICLES;
        px = new double[n];
        py = new double[n];
        vx = new double[n];
        vy = new double[n];
        size = new double[n];
        tint = new double[n];

        Random r = new Random();
        double speed = kind == Kind.MIDDLE ? 2.6 : 1.0;
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double sp = (70 + r.nextDouble() * 380) * speed;
            px[i] = x;
            py[i] = y;
            vx[i] = Math.cos(a) * sp;
            vy[i] = Math.sin(a) * sp - (kind == Kind.MIDDLE ? 10 : 40);
            size[i] = 2.5 + r.nextDouble() * (kind == Kind.MIDDLE ? 7.0 : 5.5);
            tint[i] = (hue + (r.nextDouble() - 0.5) * (kind == Kind.MIDDLE ? 140 : 70) + 360) % 360;
        }

        rayA = new double[kind == Kind.MIDDLE ? RAYS : 0];
        rayLen = new double[rayA.length];
        for (int i = 0; i < rayA.length; i++) {
            rayA[i] = (double) i / rayA.length * Math.PI * 2 + r.nextDouble() * 0.25;
            rayLen[i] = reach * (0.45 + r.nextDouble() * 0.55);
        }
    }

    /** Avanca a animacao; false quando acabou (pode ser descartado). */
    public boolean tick(double dt) {
        age += dt;
        double drag = kind == Kind.MIDDLE ? 1.15 : 1.7;
        double grav = kind == Kind.MIDDLE ? 210 : 340;
        for (int i = 0; i < px.length; i++) {
            vx[i] *= 1 - Math.min(0.9, drag * dt);
            vy[i] += grav * dt;
            px[i] += vx[i] * dt;
            py[i] += vy[i] * dt;
        }
        return age < life;
    }

    /** Zona pintada, para a overlay recortar a imagem certa. */
    public void bounds(double[] rect) {
        double pad = reach + 40;
        rect[0] = x - pad;
        rect[1] = y - pad;
        rect[2] = x + pad;
        rect[3] = y + pad;
    }

    public void paint(Graphics2D g) {
        if (kind == Kind.MIDDLE) {
            paintMiddle(g);
        } else {
            paintCorner(g);
        }
    }

    // ------------------------------------------------------------- canto
    private void paintCorner(Graphics2D g) {
        double t = Math.min(1, age / life);
        double fade = 1 - t;
        var old = g.getComposite();

        Color c = Color.getHSBColor((float) (hue / 360.0), 0.75f, 1f);
        glow(g, x, y, 150 * (0.4 + 0.6 * t), c, (float) (0.75 * fade * fade));
        glow(g, x, y, 40 * (0.5 + 0.7 * t), Color.WHITE, (float) (0.95 * fade * fade * fade));

        double wr = 18 + 190 * t;
        g.setStroke(new BasicStroke((float) (6 * fade + 0.8f)));
        g.setColor(alpha(Color.getHSBColor((float) (hue / 360.0), 0.30f, 1f), 235 * fade));
        g.drawOval((int) (x - wr), (int) (y - wr), (int) (2 * wr), (int) (2 * wr));

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, (float) fade)));
        particles(g, 0.04, 1.1 - 0.6 * t);
        g.setComposite(old);
    }

    // ------------------------------------------------------------ central
    private void paintMiddle(Graphics2D g) {
        double t = Math.min(1, age / life);
        double fade = 1 - t;
        var old = g.getComposite();

        // clarao: luz a desvanecer do centro para fora, cobre quase todo o ecra
        Color c = Color.getHSBColor((float) (hue / 360.0), 0.55f, 1f);
        glow(g, x, y, reach * (0.45 + 0.85 * t), c, (float) (0.55 * fade * fade));
        glow(g, x, y, reach * (0.16 + 0.28 * t), Color.WHITE, (float) (0.95 * fade * fade * fade));

        // estrela de raios: nasce curta, estica e afina
        double rayT = Math.min(1, t / 0.55);
        double rayFade = t < 0.55 ? 1 : Math.max(0, 1 - (t - 0.55) / 0.45);
        if (rayFade > 0.01) {
            g.setStroke(new BasicStroke((float) (9 * (1 - rayT) + 1.5f), BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) rayFade));
            for (int i = 0; i < rayA.length; i++) {
                double len = rayLen[i] * (0.15 + 0.85 * rayT);
                double inner = len * 0.18;
                g.setColor(alpha(Color.getHSBColor((float) (((hue + 180) % 360) / 360.0), 0.55f, 1f), 200));
                g.drawLine((int) (x + Math.cos(rayA[i]) * inner), (int) (y + Math.sin(rayA[i]) * inner),
                        (int) (x + Math.cos(rayA[i]) * len), (int) (y + Math.sin(rayA[i]) * len));
            }
            g.setComposite(AlphaComposite.SrcOver);
        }

        // tres ondas de choque, cada uma a sua velocidade e espessura
        double[][] waves = { { 0.55, 1.00, 11 }, { 0.80, 0.75, 7 }, { 1.15, 0.55, 4 } };
        for (double[] w : waves) {
            double wt = Math.min(1, t / w[0]);
            if (wt >= 1) {
                continue;
            }
            double wr = reach * wt * 1.05;
            double f = (1 - wt) * w[1];
            g.setStroke(new BasicStroke((float) (w[2] * f + 0.8f)));
            g.setColor(alpha(Color.getHSBColor((float) (hue / 360.0), 0.30f, 1f), (int) (255 * f)));
            g.drawOval((int) (x - wr), (int) (y - wr), (int) (2 * wr), (int) (2 * wr));
            // segundo anel na cor complementar, um pouco atras do primeiro
            g.setStroke(new BasicStroke((float) (w[2] * f * 0.5f + 0.5f)));
            g.setColor(alpha(Color.getHSBColor((float) (((hue + 150) % 360) / 360.0), 0.75f, 1f), (int) (140 * f)));
            g.drawOval((int) (x - wr * 0.92), (int) (y - wr * 0.92), (int) (2 * wr * 0.92), (int) (2 * wr * 0.92));
        }

        // nucleo a contrair-se
        double core = Math.max(4, reach * 0.075 * fade);
        circle(g, x, y, core * 2.2, alpha(Color.getHSBColor((float) (hue / 360.0), 0.35f, 1f), 150 * fade));
        circle(g, x, y, core, alpha(Color.WHITE, 245 * fade));

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, (float) fade)));
        particles(g, 0.055, 1.25 - 0.7 * t);
        g.setComposite(old);
    }

    // ------------------------------------------------------------- comum
    private void particles(Graphics2D g, double trail, double scale) {
        for (int i = 0; i < px.length; i++) {
            double s = size[i] * scale;
            g.setStroke(new BasicStroke((float) Math.max(1f, s * 0.9f), BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
            g.setColor(alpha(Color.getHSBColor((float) (tint[i] / 360.0), 0.95f, 1f), 150));
            g.drawLine((int) px[i], (int) py[i], (int) (px[i] - vx[i] * trail), (int) (py[i] - vy[i] * trail));
            g.setColor(Color.getHSBColor((float) (tint[i] / 360.0), 0.75f, 1f));
            g.fillOval((int) (px[i] - s), (int) (py[i] - s), (int) (2 * s), (int) (2 * s));
        }
    }

    private static void circle(Graphics2D g, double cx, double cy, double r, Color c) {
        g.setColor(c);
        g.fillOval((int) (cx - r), (int) (cy - r), (int) (2 * r), (int) (2 * r));
    }

    /** Halo de luz: gradiente radial do centro ate ao nada (nao um disco chapado). */
    private static void glow(Graphics2D g, double cx, double cy, double r, Color c, float intensity) {
        if (r <= 0 || intensity <= 0.01f) {
            return;
        }
        int a = (int) Math.max(0, Math.min(255, 255 * intensity));
        g.setPaint(new java.awt.RadialGradientPaint(
                new java.awt.geom.Point2D.Double(cx, cy), (float) r,
                new float[] { 0f, 0.35f, 0.7f, 1f },
                new Color[] {
                        alpha(c, a),
                        alpha(c, a * 0.55),
                        alpha(c, a * 0.18),
                        new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)
                }));
        g.fill(new java.awt.geom.Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
    }

    private static Color alpha(Color c, double a) {
        int aa = (int) Math.max(0, Math.min(255, a));
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), aa);
    }
}
