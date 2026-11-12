# mei mei DVD (Java)

A imagem do PNG anda a saltar pelo ecra e quica nas bordas, como o simbolo do
DVD. **No Windows pode flutuar por cima do desktop e das janelas, sem fundo**
(vese o que esta por tras, e o rato atravessa a overlay).

## Usar

| queres | o que fazer |
|---|---|
| 1 mei mei | duplo clique em **`run.bat`** |
| 10 mei meis ao mesmo tempo | duplo clique em **`run-10.bat`** |
| outro numero (ate 50) | `run.bat --n 25` |
| janela com fundo escuro | `run.bat --janela` |
| outra imagem | `java -jar dist\meimei-dvd.jar --png C:\outro\nome.png` |

Por o PNG na pasta do projecto com o nome **`meimei.png`** (qualquer tamanho
serve). No Windows, com o `meimeioverlay.dll` compilado, a imagem flutua por
cima do desktop e das janelas sem fundo; sem ele corre em janela escura.

Os `.bat` ja passam `--enable-native-access=ALL-UNNAMED`, necessario no JDK 24
para a overlay nativa. Se correres `java -jar` a mao sem essa opcao aparece um
WARNING no console - e so um aviso, funciona igual.

No modo com varios, cada um tem um tamanho e uma cor diferentes e a legenda de
teclas so aparece quando ha um so (para nao poluir o ecra).

## Compilar

```
build.bat            compila tudo (Java + DLL) e gera dist\meimei-dvd.jar
build-native.bat     so compila o DLL do overlay transparente
```

O `build-native.bat` usa o `cl.exe` do Visual Studio 2022 (encontra-o
automaticamente) e o `jni.h` do JDK em `C:\Program Files\Java\jdk-24`.
Se nao houver Visual Studio, o `build.bat` continua a funcionar: gera o .jar
sem o DLL e o programa corre no modo janela.

## Teclas

| tecla | o que faz |
|---|---|
| `F8` | **bloqueia/liberta todas as teclas** - podes escrever sem o screensaver reagir |
| `R` | liga/desliga a mudanca de cor nos choques |
| `T` | liga/desliga o rasto |
| `+` / `-` | mais / menos velocidade |
| `espaco` | salto extra |
| `N` | novo: escolhe direcao e posicao novas para todos |
| `Q` ou `ESC` | sair |
| `F` | ecra inteiro / janela (so no modo janela) |

Com `F8` aparece uma placa vermelha no canto do ecra enquanto estiver
bloqueado; nessa situacao nem `Q` nem `ESC` fecham o programa - so `F8` o
liberta (e depois `Q`).

No modo transparente nao ha janela focada, por isso as teclas sao lidas
constantemente em segundo plano (via `GetAsyncKeyState`). No modo janela,
arrastar com o rato move a janela.

## Cantos, explosoes e clones

Acertar num canto e **dificil de proposito**, como no screensaver original. O
criterio e geometrico: no instante exacto em que a trajetoria cruza uma parede,
a outra coordenada tem de estar a menos de `CORNER_TOL` pixeis da outra parede
(`CORNER_TOL = 8` em `src/meimei/Bouncer.java`; sobe para facilitar, desce para
castigar).

O instante e interpolado dentro do frame. Antes isto nao acontecia porque era
exigido tocar nas duas paredes *no mesmo frame* - com ~2 px por frame isso e
quase imposible. Medido por simulacao (1536x864, 240 px/s):

| bouncers | cantos por minuto |
|---|---|
| 1 | 0,25 (um a cada ~4 min) |
| 5 | 1,4 |
| 10 | 2,9 |

Quando acontece:

1. explosao **pequena no canto** (clarao + onda + particulas a cair);
2. explosao **grande no meio do ecra** (clarao branco, estrela de raios, tres
   ondas de choque e chuveiro de fagulhas);
3. nascem **+5 mei meis** a sair do canto, sem limite de total.

Como os clones tambem quicam, cada geracao aumenta a probabilidade composta de
sair outro canto - a coisa cresce sozinha. O contador no canto superior direito
(HUD) soma todos os cantos, e a consola escreve `[meimei] canto #N -> +5`.

Com muitos bouncers o programa baixa sozinho o ritmo de frames (120 -> 90 -> 60
-> 45 -> 30 fps) para nao engasgar.

## Como funciona o fundo transparente

Java/Swing nao consegue: `setOpacity()` escurece a janela toda, nao da para
deixar de fora so o fundo. Por isso ha um mini-DLL nativo
(`src/native/overlay.c`) que cria uma janela Win32 `WS_EX_LAYERED |
WS_EX_TRANSPARENT | WS_EX_TOPMOST` a cobrir o ecra virtual inteiro e a compoe
cada frame com `UpdateLayeredWindow(..., ULW_ALPHA)`, que aceita alpha por
pixel. O Java trata da fisica, do salto, das cores e do rasto; entrega so a
zona a volta do sprite como pixeis ARGB premultiplicados.

## Ficheiros

```
src/meimei/Dvd.java        janela/overlay, teclas, HUD, lancar N bouncers
src/meimei/Bouncer.java    um sprite: posicao, salto, bounce, cor, rasto, deteccao do canto
src/meimei/Boom.java       explosoes (pequena no canto, grande no meio)
src/meimei/Png.java        carregar o PNG, redimensionar, pintar cores
src/meimei/Overlay.java    JNI para a overlay transparente
src/native/overlay.c       janela layered do Windows (UpdateLayeredWindow)
LEIA-ME_AGENTES.txt        guia completo + log de comportamento (para humanos
                           e para agentes de IA que deem manutencao)
build.bat                  compila e gera dist\meimei-dvd.jar
build-native.bat           compila o DLL do overlay
run.bat                    1 mei mei
run-10.bat                 10 mei meis
meimei.png              <-- troca este ficheiro pela tua imagem
```
