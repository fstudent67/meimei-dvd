@echo off
setlocal
cd /d "%~dp0"

rem Faz o .exe com JVM embutida + zip para as pessoas descarregarem e
rem correrem sem terem Java nem de compilar nada.
rem Precisa do JDK 24 (jlink + jpackage). O build.bat trata do resto.

set "JDK=C:\Program Files\Java\jdk-24\bin"
set "JLINK=%JDK%\jlink.exe"
if not exist "%JLINK%" set "JLINK=jlink"
set "JPACKAGE=%JDK%\jpackage.exe"
if not exist "%JPACKAGE%" set "JPACKAGE=jpackage"

set "VER=1.0.0"
set "RT=build\runtime"
set "IN=build\in"
set "PKG=dist\pkg"
set "APP=%PKG%\meimei-dvd"
set "ZIP=dist\meimei-dvd-%VER%-windows-x64.zip"

call build.bat || goto :fail
if not exist dist\meimei-dvd.jar goto :fail

rmdir /s /q "%RT%" 2>nul
rmdir /s /q "%IN%" 2>nul
rmdir /s /q "%PKG%" 2>nul
del /q "%ZIP%" 2>nul
mkdir "%IN%" 2>nul
copy /y dist\meimei-dvd.jar "%IN%\" >nul

echo a criar runtime minimo (jlink)...
"%JLINK%" --add-modules java.desktop --strip-debug --no-man-pages --no-header-files --output "%RT%" || goto :fail

set "ICON="
if exist assets\meimei.ico set ICON=--icon assets\meimei.ico

echo a criar executavel (jpackage)...
"%JPACKAGE%" --type app-image --name meimei-dvd --app-version %VER% --vendor fstudent67 --input "%IN%" --main-jar meimei-dvd.jar --main-class meimei.Dvd --runtime-image "%RT%" %ICON% --java-options "--enable-native-access=ALL-UNNAMED" --dest "%PKG%" || goto :fail

rem o PNG fica ao lado do .exe (para trocar sem compilar) e ao lado do .jar
copy /y meimei.png "%APP%\meimei.png" >nul
copy /y meimei.png "%APP%\app\meimei.png" >nul
if exist LEIA-ME_PACOTE.txt copy /y LEIA-ME_PACOTE.txt "%APP%\LEIA-ME.txt" >nul

echo a compactar...
tar -a -c -f "%ZIP%" -C "%PKG%" meimei-dvd || goto :fail

for %%F in ("%ZIP%") do echo OK: %ZIP% (%%~zF bytes)
exit /b 0

:fail
echo Falha no packaging.
exit /b 1
