package meimei;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * screensaver estilo "DVD": a imagem do PNG anda a saltar pelo ecra e vai
 * quicando nas bordas, mudando de cor a cada choque e contando os cantos.
 *
 * Dois modos de pintura:
 *   TRANSPARENTE (default no Windows, precisa do meimeioverlay.dll)
 *     a imagem flutua por cima do desktop e das janelas, sem fundo; o rato
 *     atravessa a overlay e podes trabalhar normalmente por baixo.
 *   JANELA
 *     janela com fundo escuro (funciona em qualquer sistema).
 *
 * Teclas (no modo transparente nao ha janela focada, entao as teclas sao
 * lidas a todo o momento):
 *   R    liga/desliga a mudanca de cor nos choques
 *   T    rasto (trail)
 *   +/-  velocidade
 *   espaco  salto extra
 *   N    novo (escolhe uma direcao nova para todos)
 *   Q ou ESC  sair
 *   F    (so no modo janela) ecra inteiro / janela
 *   arrastar  (so no modo janela) move a janela
 */
public final class Dvd {

    // ---------------------------------------------------------------- config
    private static final int FPS = 120;
    private static final double HINT_SECONDS = 9;   // tempo que o aviso de teclas aparece
    private static final String HINT_KEYS =
            "R cores   T rasto   +/- velocidade   espaco salto   N novo   F8 bloqueia   Q sair";
    private static final Font HINT_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    private static final Font HINT_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    private static final int HINT_PAD = 10;         // margem dentro da placa
    private static final int HINT_LINE = 20;        // altura de cada linha
    private static final int HINT_GAP = 12;         // distancia abaixo da imagem
    private static final int PER_CORNER = 5;        // quantos nascem a cada canto

    // ---------------------------------------------------------------- estado
    private final BufferedImage png;       // imagem original
    private final boolean overlay;         // modo transparente?
    private final int[] screen;            // overlay: {x, y, w, h} do ecra virtual
    private final List<Bouncer> bouncers = new ArrayList<>();
    private final List<Boom> booms = new ArrayList<>();
    private final Bouncer.Options opts = new Bouncer.Options();
    private final long startedAt = System.nanoTime();

    private long last = System.nanoTime();
    private int corners;                   // cantos acertados (total)
    private boolean locked;                // F8: ignora todas as outras teclas
    private Timer timer;

    /** Metricas das fontes da legenda (medidas uma vez, sem depender do ecrano). */
    private final FontMetrics fmHint = metricsOf(HINT_FONT);
    private final FontMetrics fmHintBold = metricsOf(HINT_BOLD);

    private static FontMetrics metricsOf(Font f) {
        Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        FontMetrics fm = g.getFontMetrics(f);
        g.dispose();
        return fm;
    }
    private JFrame frame;
    private Canvas canvas;
    private boolean fullScreen;

    public Dvd(BufferedImage png, int howMany, boolean overlay) {
        this.png = png;
        this.overlay = overlay;
        this.screen = overlay ? Overlay.screen() : null;
        for (int i = 0; i < Math.max(1, howMany); i++) {
            // tamanhos um pouco diferentes ficam melhor quando ha muitos
            double scale = howMany == 1 ? 1.0 : 0.6 + Math.random() * 0.7;
            bouncers.add(new Bouncer(png, scale));
        }
    }

    private int boundsW() {
        return overlay ? screen[2] : (frame != null ? frame.getWidth() : 1100);
    }

    private int boundsH() {
        return overlay ? screen[3] : (frame != null ? frame.getHeight() : 700);
    }

    // ------------------------------------------------------------------ init
    private void show() {
        for (Bouncer b : bouncers) {
            b.reset(boundsW(), boundsH(), opts);
        }
        last = System.nanoTime();

        if (overlay) {
            timer = new Timer(1000 / FPS, e -> tick());
            timer.setCoalesce(true);
            timer.start();
            System.out.println("[meimei] modo transparente: " + bouncers.size()
                    + " bouncer(s); Q ou ESC para sair");
            return;
        }

        frame = new JFrame("mei mei DVD - F ecra inteiro, Q sair");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setUndecorated(true);
        canvas = new Canvas();
        frame.setLayout(new BorderLayout());
        frame.add(canvas, BorderLayout.CENTER);
        frame.setPreferredSize(new Dimension(1100, 700));
        frame.pack();
        frame.setLocationRelativeTo(null);
        installInput();
        frame.setVisible(true);

        Timer t = new Timer(1000 / FPS, e -> tick());
        t.setCoalesce(true);
        t.start();
        timer = t;
    }

    private void installInput() {
        canvas.addMouseListener(new MouseAdapter() {
            private int ox;
            private int oy;
            private boolean dragged;

            @Override
            public void mousePressed(MouseEvent e) {
                ox = e.getXOnScreen() - frame.getX();
                oy = e.getYOnScreen() - frame.getY();
                dragged = false;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!fullScreen) {
                    frame.setLocation(e.getXOnScreen() - ox, e.getYOnScreen() - oy);
                    dragged = true;
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (!dragged) {
                    jump();
                }
            }
        });

        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKey(e.getKeyCode());
                canvas.requestFocusInWindow();
            }
        });
        canvas.setFocusable(true);
        SwingUtilities.invokeLater(canvas::requestFocusInWindow);
    }

    private void handleKey(int code) {
        if (code == KeyEvent.VK_F8) {
            locked = !locked;
            System.out.println("[meimei] teclas " + (locked ? "BLOQUEADAS" : "livres"));
            return;
        }
        if (locked) {
            return;
        }
        switch (code) {
            case KeyEvent.VK_Q, KeyEvent.VK_ESCAPE -> quit();
            case KeyEvent.VK_F -> {
                if (!overlay) {
                    toggleFullScreen();
                }
            }
            case KeyEvent.VK_R -> {
                opts.rainbow = !opts.rainbow;
                for (Bouncer b : bouncers) {
                    b.retint();
                }
            }
            case KeyEvent.VK_T -> opts.trail = !opts.trail;
            case KeyEvent.VK_SPACE -> jump();
            case KeyEvent.VK_N -> {
                for (Bouncer b : bouncers) {
                    b.reset(boundsW(), boundsH(), opts);
                }
            }
            case KeyEvent.VK_PLUS, KeyEvent.VK_EQUALS, KeyEvent.VK_ADD -> setSpeed(opts.speed * 1.2);
            case KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT -> setSpeed(opts.speed / 1.2);
            default -> {
            }
        }
    }

    /**
     * Acertou num canto: explosao no canto, explosao no meio do ecra, e nascem
     * PER_CORNER novos mei meis a sair do canto (sem limite de total).
     */
    private void onCorner(Bouncer b, List<Bouncer> born) {
        corners++;
        double cx = b.x + b.w() / 2.0;
        double cy = b.y + b.h() / 2.0;

        booms.add(new Boom(cx, cy, b.hue, Boom.Kind.CORNER, 200));
        booms.add(new Boom(boundsW() / 2.0, boundsH() / 2.0, (b.hue + 180) % 360,
                Boom.Kind.MIDDLE, Math.min(boundsW(), boundsH()) * 0.85));

        double angle = Math.atan2(boundsH() / 2.0 - cy, boundsW() / 2.0 - cx);
        for (int i = 0; i < PER_CORNER; i++) {
            Bouncer n = new Bouncer(png, 0.55 + Math.random() * 0.65);
            n.x = Math.max(0, Math.min(boundsW() - n.w(), b.x));
            n.y = Math.max(0, Math.min(boundsH() - n.h(), b.y));
            double a = angle + (Math.random() - 0.5) * 1.6;
            n.vx = Math.cos(a) * opts.speed;
            n.vy = Math.sin(a) * opts.speed;
            n.hue = Math.random() * 360;
            n.jumpPhase = Math.random();
            n.normalize(opts);
            born.add(n);
        }
        System.out.println("[meimei] canto #" + corners + " -> +" + PER_CORNER
                + " (total " + (bouncers.size() + born.size()) + ")");
    }

    /** Com muitos bouncers o ecrano custa mais: baixa o ritmo para nao engasgar. */
    private void adaptSpeed() {
        int n = bouncers.size();
        int fps = n > 160 ? 30 : n > 90 ? 45 : n > 45 ? 60 : n > 20 ? 90 : FPS;
        if (timer != null && 1000 / fps != timer.getDelay()) {
            timer.setDelay(1000 / fps);
        }
    }

    private void jump() {
        for (Bouncer b : bouncers) {
            b.jumpPhase = 0;
        }
    }

    private void quit() {
        Overlay.close();
        System.exit(0);
    }

    /** Codigo Windows da tecla "+" e "-" (nao existem em KeyEvent). */
    private static final int VK_OEM_PLUS = 0xBB;
    private static final int VK_OEM_MINUS = 0xBD;

    /** Tecla que bloqueia/desbloqueia os atalhos (F8, nao se escreve a digitar). */
    private static final int KEY_LOCK = KeyEvent.VK_F8;

    /** No modo transparente nao ha janela: as teclas sao sondadas continuamente. */
    private void pollOverlayKeys() {
        if (Overlay.key(KEY_LOCK)) {
            handleKey(KEY_LOCK);
        }
        if (locked) {
            return;
        }
        if (Overlay.key(KeyEvent.VK_Q) || Overlay.key(KeyEvent.VK_ESCAPE)) {
            quit();
        }
        if (Overlay.key(KeyEvent.VK_R)) {
            handleKey(KeyEvent.VK_R);
        }
        if (Overlay.key(KeyEvent.VK_T)) {
            handleKey(KeyEvent.VK_T);
        }
        if (Overlay.key(KeyEvent.VK_N)) {
            handleKey(KeyEvent.VK_N);
        }
        if (Overlay.key(KeyEvent.VK_SPACE)) {
            jump();
        }
        if (Overlay.key(VK_OEM_PLUS) || Overlay.key(KeyEvent.VK_ADD)) {
            setSpeed(opts.speed * 1.2);
        }
        if (Overlay.key(VK_OEM_MINUS) || Overlay.key(KeyEvent.VK_SUBTRACT)) {
            setSpeed(opts.speed / 1.2);
        }
    }

    private void setSpeed(double s) {
        opts.speed = Math.max(40, Math.min(1400, s));
        for (Bouncer b : bouncers) {
            b.normalize(opts);
        }
    }

    private void toggleFullScreen() {
        fullScreen = !fullScreen;
        frame.dispose();
        if (fullScreen) {
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            frame.setAlwaysOnTop(true);
        } else {
            frame.setAlwaysOnTop(false);
            frame.setSize(1100, 700);
            frame.setLocationRelativeTo(null);
        }
        frame.setVisible(true);
        clampToPanel();
        SwingUtilities.invokeLater(canvas::requestFocusInWindow);
    }

    private void clampToPanel() {
        for (Bouncer b : bouncers) {
            b.x = Math.max(0, Math.min(Math.max(0, canvas.getWidth() - b.w()), b.x));
            b.y = Math.max(0, Math.min(Math.max(0, canvas.getHeight() - b.h()), b.y));
        }
    }

    // ---------------------------------------------------------------- fisica
    private void tick() {
        if (overlay) {
            pollOverlayKeys();
        }

        long now = System.nanoTime();
        double dt = Math.min(0.05, (now - last) / 1_000_000_000.0);
        last = now;

        int bw = boundsW();
        int bh = boundsH();

        List<Bouncer> born = new ArrayList<>();
        for (Bouncer b : bouncers) {
            if (b.tick(dt, bw, bh, opts)) {
                onCorner(b, born);
            }
        }
        if (!born.isEmpty()) {
            bouncers.addAll(born);
            adaptSpeed();
        }

        booms.removeIf(e -> !e.tick(dt));

        if (overlay) {
            paintOverlay();
        } else if (canvas != null) {
            canvas.repaint();
        }
    }

    // ------------------------------------------------------- modo transparente
    /** Zona onde a placa de "teclas bloqueadas" e pintada, em coord. de ecra. */
    private static final int BADGE_X = 10;
    private static final int BADGE_Y = 10;
    private static final int BADGE_W = 330;
    private static final int BADGE_H = 34;
    private static final String BADGE_TEXT = "TECLAS BLOQUEADAS  -  F8 para libertar";

    /** Poe na overlay so a zona a volta dos bouncers, sem fundo, por cima de tudo. */
    private void paintOverlay() {
        double[] r = { Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE };
        double[] t = new double[4];
        for (Bouncer b : bouncers) {
            b.bounds(t, opts);
            union(r, t);
        }
        for (Boom e : booms) {
            e.bounds(t);
            union(r, t);
        }
        if (locked) {
            t[0] = BADGE_X;
            t[1] = BADGE_Y;
            t[2] = BADGE_X + badgeWidth();
            t[3] = BADGE_Y + BADGE_H;
            union(r, t);
        }
        if (bouncers.size() == 1) {
            // a legenda e maior que o sprite: sem isto ficava cortada na borda
            // da zona que pintamos
            int[] hb = hintBox(bouncers.get(0));
            t[0] = hb[0];
            t[1] = hb[1];
            t[2] = hb[0] + hb[2];
            t[3] = hb[1] + hb[3];
            union(r, t);
        }

        double left = Math.max(0, Math.floor(r[0]));
        double top = Math.max(0, Math.floor(r[1]));
        double right = Math.min(screen[2], Math.ceil(r[2]));
        double bottom = Math.min(screen[3], Math.ceil(r[3]));
        int cw = (int) (right - left);
        int ch = (int) (bottom - top);
        if (cw <= 0 || ch <= 0) {
            return;
        }

        BufferedImage img = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        hints(g);
        g.translate(-left, -top);
        for (Bouncer b : bouncers) {
            b.paint(g, opts);
        }
        for (Boom e : booms) {
            e.paint(g);
        }
        if (bouncers.size() == 1) {
            drawHint(g, bouncers.get(0));
        }
        if (locked) {
            drawLockBadge(g);
        }
        g.dispose();

        Overlay.paint(img, screen[0] + (int) left, screen[1] + (int) top);
    }

    /** Expande r para incluir add. */
    private static void union(double[] r, double[] add) {
        r[0] = Math.min(r[0], add[0]);
        r[1] = Math.min(r[1], add[1]);
        r[2] = Math.max(r[2], add[2]);
        r[3] = Math.max(r[3], add[3]);
    }

    /** Placa a dizer que os atalhos estao ignorados (F8 volta a liga-los). */
    private void drawLockBadge(Graphics2D g) {
        int w = badgeWidth();
        g.setColor(new Color(28, 10, 12, 205));
        g.fillRoundRect(BADGE_X, BADGE_Y, w, BADGE_H, 12, 12);
        g.setStroke(new java.awt.BasicStroke(2f));
        g.setColor(new Color(0xFF6B6B));
        g.drawRoundRect(BADGE_X, BADGE_Y, w, BADGE_H, 12, 12);
        g.setColor(new Color(0xFFE1E1));
        g.setFont(HINT_BOLD);
        g.drawString(BADGE_TEXT, BADGE_X + HINT_PAD, BADGE_Y + HINT_PAD / 2 + fmHintBold.getAscent());
    }

    /** Largura real da placa de bloqueio, medida com a fonte (nunca corta texto). */
    private int badgeWidth() {
        return Math.max(BADGE_W, fmHintBold.stringWidth(BADGE_TEXT) + 2 * HINT_PAD);
    }

    private static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    /**
     * Rectangulo da placa de teclas, {x, y, largura, altura}, em coordenadas de
     * ecra. A largura e medida com as fontes reais, por isso o texto cabe sempre;
     * se nao couber por baixo do bouncer, vai para cima, e nunca sai do ecra.
     */
    private int[] hintBox(Bouncer b) {
        String c = corners + " cantos";
        int textW = Math.max(fmHint.stringWidth(HINT_KEYS), fmHintBold.stringWidth(c));
        int w = textW + 2 * HINT_PAD;
        int h = 2 * HINT_LINE + 2 * HINT_PAD;

        int x = (int) Math.round(b.x + b.w() / 2.0 - w / 2.0);
        int y = (int) Math.round(b.y + b.h() + HINT_GAP);

        int maxX = boundsW() - w - 4;
        if (maxX >= 4) {
            x = Math.max(4, Math.min(maxX, x));
        }
        if (y + h > boundsH() - 4) {
            y = Math.max(4, (int) Math.round(b.y) - h - HINT_GAP);
        }
        return new int[] { x, y, w, h };
    }

    /** Aviso de teclas, com placa escura para se ler sobre qualquer fundo. */
    private void drawHint(Graphics2D g, Bouncer b) {
        double elapsed = (System.nanoTime() - startedAt) / 1_000_000_000.0;
        float a = elapsed > HINT_SECONDS ? 0.45f : 1f;

        int[] box = hintBox(b);
        int bx = box[0];
        int by = box[1];
        int bw = box[2];
        int bh = box[3];

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        g.setColor(new Color(10, 12, 20, 190));
        g.fillRoundRect(bx, by, bw, bh, 12, 12);

        g.setColor(new Color(0xE6EBFF));
        g.setFont(HINT_FONT);
        g.drawString(HINT_KEYS, bx + HINT_PAD, by + HINT_PAD + fmHint.getAscent());
        g.setFont(HINT_BOLD);
        g.drawString(corners + " cantos", bx + HINT_PAD, by + HINT_PAD + HINT_LINE + fmHintBold.getAscent());
        g.setComposite(AlphaComposite.SrcOver);
    }

    // -------------------------------------------------------------- modo janela
    private final class Canvas extends JPanel {
        private static final long serialVersionUID = 1L;

        Canvas() {
            setOpaque(true);
            setBackground(new Color(0x07070C));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            hints(g);
            for (Bouncer b : bouncers) {
                b.paint(g, opts);
            }
            for (Boom e : booms) {
                e.paint(g);
            }
            drawHud(g);
            g.dispose();
        }

        private void drawHud(Graphics2D g) {
            g.setColor(new Color(0x8A93B8));
            g.setFont(getFont().deriveFont(12f));
            g.drawString("mei mei DVD x" + bouncers.size()
                    + "  |  F8 bloqueia teclas   R cores   T rasto   +/- velocidade   espaco salto   N novo   F ecras   Q sair",
                    12, getHeight() - 14);
            if (locked) {
                drawLockBadge(g);
            }
            String corner = corners + " cantos";
            g.setFont(getFont().deriveFont(18f));
            g.drawString(corner, getWidth() - g.getFontMetrics().stringWidth(corner) - 14, 26);
        }
    }

    // ------------------------------------------------------------------ main
    /** true se o texto e so digitos (usado para ler --n). */
    private static boolean isDigits(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** Limita racionalmente o numero de bouncers. */
    private static int clampN(int n) {
        return Math.max(1, Math.min(50, n));
    }

    /** Lista de comandos e teclas (java -jar meimei-dvd.jar --help). */
    private static void help() {
        System.out.println("""
                mei mei DVD - screensaver estilo DVD com o teu PNG

                USO
                  java -jar dist\\meimei-dvd.jar [--png <ficheiro>] [--n <num>] [--janela]

                COMANDOS
                  --png <caminho>      usar outra imagem (default: meimei.png)
                  --n <num>            numero de bouncers no arranque (1..50)
                  --n=<num>            forma alternativa
                  --many <num>         sinonimo de --n
                  --dez | --10         atalho para --n 10
                  --janela | --window  nao usar a overlay transparente
                  --help | -h          esta lista

                TECLAS
                  F8  bloqueia/liberta as teclas (para poder escrever)
                  R   liga/desliga mudanca de cor nos choques
                  T   liga/desliga rasto
                  + - mais/menos velocidade
                  espaco  salto extra
                  N   nova direcao e posicao para todos
                  F   ecra inteiro / janela (so no modo janela)
                  Q ou ESC  sair

                NOTAS
                  Acertar num canto: explosao no canto + explosao no meio do
                  ecra + nascem +5 bouncers (sem limite).
                  No Windows, com meimeioverlay.dll, a imagem flutua por cima
                  do desktop sem fundo; sem DLL corre em janela escura.
                  Ver LEIA-ME_AGENTES.txt para detalhes de manutencao.
                """);
    }

    public static void main(String[] args) {
        boolean wantWindow = false;
        int howMany = 1;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("--png".equalsIgnoreCase(a) && i + 1 < args.length) {
                System.setProperty("meimei.png", args[++i]);
            } else if ("--janela".equalsIgnoreCase(a) || "--window".equalsIgnoreCase(a)) {
                wantWindow = true;
            } else if (("--n".equals(a) || "--many".equals(a)) && i + 1 < args.length && isDigits(args[i + 1])) {
                howMany = clampN(Integer.parseInt(args[++i]));
            } else if (a.startsWith("--n=") && isDigits(a.substring(4))) {
                howMany = clampN(Integer.parseInt(a.substring(4)));
            } else if ("--dez".equalsIgnoreCase(a) || "--10".equals(a)) {
                howMany = 10;
            } else if ("--help".equalsIgnoreCase(a) || "-h".equalsIgnoreCase(a) || "/?".equals(a)) {
                help();
                return;
            }
        }

        File png = Png.find();
        if (png == null) {
            String msg = "Nao encontrei o meimei.png.\n\n"
                    + "Coloca a imagem (meimei.png, de preferencia com fundo transparente)\n"
                    + "na mesma pasta do meimei-dvd.jar e abre outra vez,\n"
                    + "ou executa:  java -jar meimei-dvd.jar --png caminho\\imagem.png";
            System.err.println(msg);
            if (!java.awt.GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(null, msg, "mei mei DVD", JOptionPane.WARNING_MESSAGE);
            }
            return;
        }

        BufferedImage img;
        try {
            img = Png.read(png);
        } catch (Exception e) {
            System.err.println("Nao consegui abrir " + png + ": " + e.getMessage());
            return;
        }
        System.out.println("[meimei] a usar " + png + " (" + img.getWidth() + "x" + img.getHeight() + ")");

        boolean overlay = !wantWindow && Overlay.available();
        if (!overlay && !wantWindow) {
            System.out.println("[meimei] sem meimeioverlay.dll -> modo janela com fundo escuro");
            System.out.println("[meimei] para o fundo transparente: build-native.bat e depois run.bat");
        }
        final BufferedImage sprite = img;
        final int count = howMany;
        SwingUtilities.invokeLater(() -> new Dvd(sprite, count, overlay).show());
    }
}
