package meimei;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Um "mei mei" a saltar: guarda a posicao, a direcao, a cor actual e o rasto,
 * e sabe mover-se e desenhar-se. O Dvd trata da janela/overlay e das teclas;
 * pode haver varios destes em simultaneo (--n 10).
 */
public final class Bouncer {

    /** Opcoes partilhadas por todos os bouncers (mudam com as teclas). */
    public static final class Options {
        public double speed = 240;        // px/segundo
        public double jumpHeight = 42;    // px
        public double jumpPeriod = 0.75;  // segundos por salto
        public int trailMax = 14;
        public boolean rainbow = true;    // mudar de cor a cada choque
        public boolean trail = true;
    }

    /**
     * Folga, em pixeis, para contar como canto: no instante em que cruza uma
     * parede, a outra coordenada tem de estar a menos disto da outra parede.
     * 8 px e dificil mas acontece; sobe para facilitar, desce para castigar.
     */
    private static final double CORNER_TOL = 8;

    private final BufferedImage base;     // PNG redimensionado para este bouncer
    private final Deque<double[]> trail = new ArrayDeque<>();

    private BufferedImage tintedCache;
    private boolean tintDirty = true;

    double x;
    double y;
    double vx;
    double vy;
    double hue;
    double jumpPhase;

    public Bouncer(BufferedImage png, double scale) {
        base = Png.scale(png, Math.max(24, (int) Math.round(180 * scale)));
    }

    public int w() {
        return base.getWidth();
    }

    public int h() {
        return base.getHeight();
    }

    /** Area pintada por este bouncer (inclui rasto), para a overlay recortar. */
    public void bounds(double[] rect, Options o) {
        double pad = o.speed * o.trailMax / 120.0 + 20;
        rect[0] = x - pad;
        rect[1] = y - pad;
        rect[2] = x + w() + pad;
        rect[3] = y + h() + pad;
    }

    /** Lanca o bouncer para uma direcao aleatoria dentro dos limites dados. */
    public void reset(int bw, int bh, Options o) {
        int minX = 40;
        int minY = 40;
        x = minX + Math.random() * Math.max(1, bw - w() - minX * 2);
        y = minY + Math.random() * Math.max(1, bh - h() - minY * 2);
        double angle = Math.random() * Math.PI / 2 - Math.PI / 4 + (Math.random() < 0.5 ? Math.PI : 0);
        vx = Math.cos(angle) * o.speed;
        vy = Math.sin(angle) * o.speed;
        hue = Math.random() * 360;
        jumpPhase = Math.random();
        normalize(o);
    }

    public void normalize(Options o) {
        double len = Math.hypot(vx, vy);
        if (len < 1e-6) {
            vx = o.speed;
            vy = 0;
            len = o.speed;
        }
        vx = vx / len * o.speed;
        vy = vy / len * o.speed;
    }

    /** Avanca o tempo; true se acertou num canto (counted). */
    public boolean tick(double dt, int bw, int bh, Options o) {
        double fromX = x;
        double fromY = y;
        x += vx * dt;
        y += vy * dt;

        // trajeto real, antes de ser cortado pelas paredes: e este que se
        // compara com o ponto do canto (o clamp cairia sempre em cima dele)
        double rawX = x;
        double rawY = y;

        double maxX = Math.max(0, bw - w());
        double maxY = Math.max(0, bh - h());

        boolean hitX = rawX < 0 || rawX > maxX;
        boolean hitY = rawY < 0 || rawY > maxY;

        // "bater no canto" = no instante exacto em que a trajetoria cruza uma
        // parede, a outra coordenada estar a menos de CORNER_TOL pixeis da outra
        // parede. O instante e interpolado dentro do frame, por isso nao
        // depende de a sorte apanhar os dois limites no mesmo frame (era isso
        // que tornava o canto praticamente impossivel).
        boolean corner = false;
        if (hitX) {
            double wall = rawX < 0 ? 0 : maxX;
            double t = (wall - fromX) / (rawX - fromX);
            double yAt = fromY + (rawY - fromY) * t;
            corner = yAt <= CORNER_TOL || yAt >= maxY - CORNER_TOL;
            x = wall;
            vx = Math.abs(vx) * (rawX < 0 ? 1 : -1);
        }
        if (hitY) {
            double wall = rawY < 0 ? 0 : maxY;
            double t = (wall - fromY) / (rawY - fromY);
            double xAt = fromX + (rawX - fromX) * t;
            corner = corner || xAt <= CORNER_TOL || xAt >= maxX - CORNER_TOL;
            y = wall;
            vy = Math.abs(vy) * (rawY < 0 ? 1 : -1);
        }

        if (hitX || hitY) {
            normalize(o);
            jumpPhase = 0;
            if (o.rainbow) {
                hue = (hue + (corner ? 90 : 31)) % 360;
                tintDirty = true;
            }
        }

        jumpPhase = (jumpPhase + dt / o.jumpPeriod) % 1.0;

        trail.addFirst(new double[] { x, y });
        while (trail.size() > o.trailMax) {
            trail.removeLast();
        }
        return corner;
    }

    /** Redesenha com a cor nova (chamado quando as opcoes mudam). */
    public void retint() {
        tintDirty = true;
    }

    /** Desenha rasto + sombra + sprite em coordenadas de ecra. */
    public void paint(Graphics2D g, Options o) {
        BufferedImage img = tinted(o);
        int w = w();
        int h = h();

        if (o.trail) {
            int i = 0;
            for (double[] p : trail) {
                float a = 0.16f * (1f - (float) i / o.trailMax);
                if (a <= 0.01f) {
                    break;
                }
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
                g.drawImage(img, (int) p[0], (int) p[1], null);
                i++;
            }
            g.setComposite(AlphaComposite.SrcOver);
        }

        // salto: sobe e desce + squash & stretch
        double t = jumpPhase;
        double hop = -o.jumpHeight * Math.sin(Math.PI * t);
        double stretch = 1 + 0.12 * Math.sin(Math.PI * t);
        double squash = 1 - 0.09 * Math.sin(Math.PI * t);

        double cx = x + w / 2.0;
        double bottom = y + hop + h;

        // sombra no chao
        double ground = 1 - Math.abs(hop) / (o.jumpHeight + 1);
        int shadowW = (int) (w * 0.7 * ground);
        g.setColor(new Color(0, 0, 0, (int) (90 * ground)));
        g.fillOval((int) (cx - shadowW / 2.0), (int) (y + h - 8), shadowW, (int) (14 * ground));

        AffineTransform at = new AffineTransform();
        at.translate(cx, bottom);
        at.scale(vx < 0 ? -stretch : stretch, squash);
        at.translate(-w / 2.0, -h);
        g.drawImage(img, at, null);
    }

    /** PNG pintado com a cor actual (sem modo cores devolve o original). */
    private BufferedImage tinted(Options o) {
        if (!o.rainbow) {
            return base;
        }
        if (tintedCache == null || tintDirty) {
            tintDirty = false;
            tintedCache = Png.tint(base, Color.getHSBColor((float) (hue / 360.0), 0.55f, 1f));
        }
        return tintedCache;
    }
}
