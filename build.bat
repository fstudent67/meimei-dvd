@echo off
setlocal
cd /d "%~dp0"

set "JAVAC=C:\Program Files\Java\jdk-24\bin\javac.exe"
if not exist "%JAVAC%" set "JAVAC=javac"
set "JAR=C:\Program Files\Java\jdk-24\bin\jar.exe"
if not exist "%JAR%" set "JAR=jar"

set "CLS=build\classes"
set "NAT=build\native"

rmdir /s /q build 2>nul
mkdir "%CLS%" 2>nul
mkdir "%NAT%" 2>nul
mkdir dist 2>nul

rem --- overlay transparente (opcional): sem Visual Studio corre no modo janela
call build-native.bat >nul 2>nul
if exist "%NAT%\meimeioverlay.dll" (echo DLL: ok) else (echo DLL: nao compilado - modo janela apenas)

"%JAVAC%" -Xlint:-options -encoding UTF-8 -d "%CLS%" src\meimei\*.java || goto :fail

if exist "%NAT%\meimeioverlay.dll" (
  "%JAR%" --create --file dist\meimei-dvd.jar --main-class meimei.Dvd -C "%CLS%" . -C "%NAT%" meimeioverlay.dll || goto :fail
) else (
  "%JAR%" --create --file dist\meimei-dvd.jar --main-class meimei.Dvd -C "%CLS%" . || goto :fail
)

echo OK: dist\meimei-dvd.jar
exit /b 0

:fail
echo Falha na compilacao.
exit /b 1
