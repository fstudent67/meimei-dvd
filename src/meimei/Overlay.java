package meimei;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Overlay transparente por pixel no Windows (janela "layered").
 *
 * Java puro nao consegue desenhar por cima do desktop sem fundo: o maximo que
 * Swing faz e escurecer/transparentar a janela toda. Aqui usa-se a API Win32
 * UpdateLayeredWindow com ULW_ALPHA, que aceita alpha por pixel e uma janela
 * "click-through" (o rato atravessa-a e trabalha-se normalmente por baixo).
 *
 * Precisa do meimeioverlay.dll (ver build.bat); se nao existir, o programa
 * corre no modo janela normal com fundo escuro.
 */
public final class Overlay {

    private Overlay() {
    }

    private static boolean loaded;

    // --- natives (ver src/native/overlay.c) ---------------------------
    private static native boolean nativeInit();

    private static native boolean nativePaint(int[] pixels, int x, int y, int w, int h);

    private static native void nativeClose();

    private static native boolean nativeKey(int vk);

    private static native int[] nativeScreen();

    // --- carregamento -------------------------------------------------
    /** Tenta carregar o DLL; true se o overlay transparente esta disponivel. */
    public static synchronized boolean available() {
        if (loaded) {
            return true;
        }
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return false;
        }
        File dll = findDll();
        if (dll == null) {
            return false;
        }
        try {
            System.load(dll.getAbsolutePath());
            loaded = nativeInit();
            if (loaded) {
                System.out.println("[meimei] overlay transparente activo (" + dll + ")");
            }
        } catch (Throwable t) {
            System.out.println("[meimei] overlay indisponivel: " + t);
            loaded = false;
        }
        return loaded;
    }

    /** Procura o DLL ao lado do .jar, na pasta actual, ou dentro do .jar. */
    private static File findDll() {
        String name = "meimeioverlay.dll";
        File direct = new File(name);
        if (direct.isFile()) {
            return direct;
        }
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            File dir = new File(entry).getAbsoluteFile().getParentFile();
            if (dir != null) {
                File f = new File(dir, name);
                if (f.isFile()) {
                    return f;
                }
            }
        }
        try (InputStream in = Overlay.class.getResourceAsStream("/" + name)) {
            if (in != null) {
                Path tmp = Path.of(System.getProperty("java.io.tmpdir"), "meimei", name);
                Files.createDirectories(tmp.getParent());
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                return tmp.toFile();
            }
        } catch (Exception ignored) {
            // sem DLL -> modo janela
        }
        return null;
    }

    // --- uso ----------------------------------------------------------
    /** Ecra virtual: {x, y, largura, altura}. */
    public static int[] screen() {
        int[] s = nativeScreen();
        return s != null ? s : new int[] { 0, 0, 1280, 720 };
    }

    /**
     * Desenha `img` no ecra (coordenadas absolutas), com alpha por pixel.
     * converte ARGB -> ARGB premultiplicado, que e o que a API pede.
     */
    public static boolean paint(BufferedImage img, int x, int y) {
        if (!loaded) {
            return false;
        }
        int w = img.getWidth();
        int h = img.getHeight();
        int[] px = new int[w * h];
        img.getRGB(0, 0, w, h, px, 0, w);
        for (int i = 0; i < px.length; i++) {
            int argb = px[i];
            int a = (argb >>> 24) & 0xFF;
            if (a == 0) {
                px[i] = 0;
                continue;
            }
            if (a == 255) {
                continue;
            }
            int r = ((argb >> 16) & 0xFF) * a / 255;
            int g = ((argb >> 8) & 0xFF) * a / 255;
            int b = (argb & 0xFF) * a / 255;
            px[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        return nativePaint(px, x, y, w, h);
    }

    /** true se a tecla (VK_*) foi premida desde a ultima vez que perguntaste. */
    public static boolean key(int vk) {
        return loaded && nativeKey(vk);
    }

    public static void close() {
        if (loaded) {
            nativeClose();
        }
    }
}
