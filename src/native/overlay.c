/*
 * overlay.c - janela "layered" transparente por pixel (Windows)
 *
 * Permite desenhar o sprite por cima do ecra inteiro sem fundo preto:
 * o que nao e sprite fica totalmente transparente e o rato atravessa a janela.
 * Java puro nao consegue isto (setOpacity escurece tudo); isto usa a API
 * Win32 UpdateLayeredWindow com ULW_ALPHA.
 */
#include <windows.h>
#include <jni.h>

static HWND          g_hwnd = NULL;
static HDC           g_mem  = NULL;
static HBITMAP       g_bmp  = NULL;
static void         *g_bits = NULL;
static HGDIOBJ       g_old  = NULL;
static int           g_w = 0, g_h = 0;

static void destroy_overlay(void)
{
    if (g_mem) {
        if (g_bmp) { SelectObject(g_mem, g_old); DeleteObject(g_bmp); }
        DeleteDC(g_mem);
    }
    if (g_hwnd) DestroyWindow(g_hwnd);
    g_hwnd = NULL; g_mem = NULL; g_bmp = NULL; g_bits = NULL; g_old = NULL;
    g_w = g_h = 0;
}

/* Cria a janela cobrindo o ecra virtual, layered + click-through + topmost. */
JNIEXPORT jboolean JNICALL Java_meimei_Overlay_nativeInit(JNIEnv *env, jclass cls)
{
    WNDCLASS wc;
    if (!g_hwnd) {
        ZeroMemory(&wc, sizeof wc);
        wc.lpfnWndProc   = DefWindowProc;
        wc.hInstance     = GetModuleHandle(NULL);
        wc.lpszClassName = "MeiMeiOverlay";
        RegisterClass(&wc);

        g_hwnd = CreateWindowEx(
            WS_EX_LAYERED | WS_EX_TRANSPARENT | WS_EX_TOPMOST | WS_EX_TOOLWINDOW,
            "MeiMeiOverlay", "", WS_POPUP,
            GetSystemMetrics(SM_XVIRTUALSCREEN), GetSystemMetrics(SM_YVIRTUALSCREEN),
            GetSystemMetrics(SM_CXVIRTUALSCREEN), GetSystemMetrics(SM_CYVIRTUALSCREEN),
            NULL, NULL, wc.hInstance, NULL);
        if (!g_hwnd) return JNI_FALSE;
        ShowWindow(g_hwnd, SW_SHOWNOACTIVATE);
    }
    return JNI_TRUE;
}

/* Cria (ou recria) o bitmap do tamanho do ecra. */
static int ensure_bitmap(int w, int h)
{
    BITMAPINFO bi;

    if (g_bits && g_w == w && g_h == h) return 1;
    if (g_mem) { SelectObject(g_mem, g_old); DeleteObject(g_bmp); DeleteDC(g_mem); }
    g_mem = CreateCompatibleDC(NULL);
    if (!g_mem) return 0;

    ZeroMemory(&bi, sizeof bi);
    bi.bmiHeader.biSize        = sizeof(BITMAPINFOHEADER);
    bi.bmiHeader.biWidth       = w;
    bi.bmiHeader.biHeight      = -h;          /* top-down */
    bi.bmiHeader.biPlanes      = 1;
    bi.bmiHeader.biBitCount    = 32;
    bi.bmiHeader.biCompression = BI_RGB;

    g_bmp = CreateDIBSection(g_mem, &bi, DIB_RGB_COLORS, &g_bits, NULL, 0);
    if (!g_bmp) { DeleteDC(g_mem); g_mem = NULL; return 0; }
    g_old = SelectObject(g_mem, g_bmp);
    g_w = w; g_h = h;
    return 1;
}

/*
 * Copia `len` pixeis ARGB (premultiplicados) para a janela e faz o compose.
 * x,y = canto superior esquerdo da imagem; w,h = tamanho da imagem.
 */
JNIEXPORT jboolean JNICALL Java_meimei_Overlay_nativePaint
    (JNIEnv *env, jclass cls, jintArray pixels, jint x, jint y, jint w, jint h)
{
    jint *src;
    BLENDFUNCTION bf;
    POINT srcPos, dstPos;
    SIZE  size;
    HDC   screen;

    if (!g_hwnd || w <= 0 || h <= 0) return JNI_FALSE;
    if ((*env)->GetArrayLength(env, pixels) < w * h) return JNI_FALSE;
    if (!ensure_bitmap(w, h)) return JNI_FALSE;

    src = (*env)->GetIntArrayElements(env, pixels, NULL);
    if (!src) return JNI_FALSE;
    CopyMemory(g_bits, src, (SIZE_T) w * h * sizeof(jint));
    (*env)->ReleaseIntArrayElements(env, pixels, src, JNI_ABORT);

    screen = GetDC(NULL);
    bf.BlendOp             = AC_SRC_OVER;
    bf.BlendFlags          = 0;
    bf.SourceConstantAlpha = 255;
    bf.AlphaFormat         = AC_SRC_ALPHA;

    srcPos.x = 0; srcPos.y = 0;
    dstPos.x = x; dstPos.y = y;
    size.cx  = w; size.cy = h;

    UpdateLayeredWindow(g_hwnd, screen, &dstPos, &size, g_mem, &srcPos, 0, &bf, ULW_ALPHA);
    ReleaseDC(NULL, screen);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_meimei_Overlay_nativeClose(JNIEnv *env, jclass cls)
{
    destroy_overlay();
}

/* Tecla agora pressionada (a janela e click-through, logo nao recebe teclas). */
JNIEXPORT jboolean JNICALL Java_meimei_Overlay_nativeKey(JNIEnv *env, jclass cls, jint vk)
{
    return (GetAsyncKeyState(vk) & 1) ? JNI_TRUE : JNI_FALSE;
}

/* Dimensoes do ecra virtual (para o Java saber onde pintar). */
JNIEXPORT jintArray JNICALL Java_meimei_Overlay_nativeScreen(JNIEnv *env, jclass cls)
{
    jint *v;
    jintArray a = (*env)->NewIntArray(env, 4);
    if (!a) return NULL;
    v = (*env)->GetIntArrayElements(env, a, NULL);
    v[0] = GetSystemMetrics(SM_XVIRTUALSCREEN);
    v[1] = GetSystemMetrics(SM_YVIRTUALSCREEN);
    v[2] = GetSystemMetrics(SM_CXVIRTUALSCREEN);
    v[3] = GetSystemMetrics(SM_CYVIRTUALSCREEN);
    (*env)->ReleaseIntArrayElements(env, a, v, 0);
    return a;
}
