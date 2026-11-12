@echo off
setlocal
cd /d "%~dp0"
set "JAVA=C:\Program Files\Java\jdk-24\bin\javaw.exe"
if not exist "%JAVA%" set "JAVA=javaw"
if not exist "dist\meimei-dvd.jar" call build.bat
rem argumentos extra sao passados ao programa, p.ex.: run.bat --janela
start "" "%JAVA%" --enable-native-access=ALL-UNNAMED -jar dist\meimei-dvd.jar --png "%~dp0meimei.png" %*
