@echo off
setlocal
rem Compila o meimeioverlay.dll (overlay transparente por pixel) com o cl.exe
rem do Visual Studio. Resultado: build\native\meimeioverlay.dll
cd /d "%~dp0"

set "VS="
for %%V in ("Community" "Professional" "Enterprise" "BuildTools") do (
  if exist "C:\Program Files\Microsoft Visual Studio\2022\%%~V\VC\Auxiliary\Build\vcvars64.bat" (
    set "VS=C:\Program Files\Microsoft Visual Studio\2022\%%~V"
  )
)
if not defined VS (
  echo Nao encontrei o Visual Studio 2022 ^(cl.exe^). Sem DLL o programa corre no modo janela.
  exit /b 1
)

set "JAVAHOME=C:\Program Files\Java\jdk-24"
if not exist "%JAVAHOME%\include\jni.h" (
  echo Nao encontrei %JAVAHOME%\include\jni.h
  exit /b 1
)

if not exist "build\native" mkdir "build\native"

call "%VS%\VC\Auxiliary\Build\vcvars64.bat" >nul
if errorlevel 1 goto :fail

pushd "build\native"
cl /nologo /O2 /MT ^
   /I "%JAVAHOME%\include" /I "%JAVAHOME%\include\win32" ^
   ..\..\src\native\overlay.c ^
   /link /DLL /OUT:meimeioverlay.dll user32.lib gdi32.lib
set "RC=%ERRORLEVEL%"
del /q *.obj *.lib *.exp 2>nul
popd

if not "%RC%"=="0" goto :fail
if not exist "build\native\meimeioverlay.dll" goto :fail

echo OK: build\native\meimeioverlay.dll
exit /b 0

:fail
echo Falha ao compilar o DLL.
exit /b 1
