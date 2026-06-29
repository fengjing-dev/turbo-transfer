@echo off
setlocal

set "APP_NAME=TurboTransfer"
set "APP_VERSION=1.0.0"
set "APP_VENDOR=Fatina"
set "APP_STAGE_DIR=target\app"
set "LOG_DIR=log"
set "LAUNCHER_LOG_FILE=launcher.log"
set "MAIN_JAR=%APP_NAME%.jar"
set "DESKTOP_MAIN_CLASS=com.fatina.transfer.desktop.DesktopLauncher"
set "DIST_DIR=dist"
set "INPUT_DIR=%DIST_DIR%\input"
set "PORTABLE_DIR=%DIST_DIR%\portable"
set "PORTABLE_ZIP=%DIST_DIR%\%APP_NAME%-portable-%APP_VERSION%.zip"
set "SETUP_EXE=%APP_NAME%-Setup-%APP_VERSION%.exe"
set "DEFAULT_WIX=D:\develop\tools\wix314"

set "APP_HOME=%~dp0"
if "%APP_HOME:~-1%"=="\" set "APP_HOME=%APP_HOME:~0,-1%"
cd /d "%APP_HOME%"
if not exist "%APP_HOME%\%LOG_DIR%" mkdir "%APP_HOME%\%LOG_DIR%"
set "LAUNCHER_LOG=%APP_HOME%\%LOG_DIR%\%LAUNCHER_LOG_FILE%"

REM ---- 1. Build distribution files ----
echo [TurboTransfer] Building distribution before packaging...
call "%APP_HOME%\build-dist.bat"
if errorlevel 1 goto :build_fail

if not exist "%APP_STAGE_DIR%\%MAIN_JAR%" goto :missing
if not exist "%APP_STAGE_DIR%\lib" goto :missing

REM ---- 2. Prepare clean input layout ----
echo [TurboTransfer] Preparing packaging layout...
if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"
if exist "%DIST_DIR%" goto :locked
mkdir "%INPUT_DIR%\lib"
xcopy /y "%APP_STAGE_DIR%\%MAIN_JAR%" "%INPUT_DIR%\" >nul
if errorlevel 1 goto :fail
xcopy /e /i /y "%APP_STAGE_DIR%\lib\*" "%INPUT_DIR%\lib\" >nul
if errorlevel 1 goto :fail

REM ---- 3. Portable build (app-image -> zip), no WiX required ----
echo [TurboTransfer] Building portable app-image...
jpackage ^
  --type app-image ^
  --dest "%PORTABLE_DIR%" ^
  --name "%APP_NAME%" ^
  --app-version "%APP_VERSION%" ^
  --vendor "%APP_VENDOR%" ^
  --input "%INPUT_DIR%" ^
  --main-class %DESKTOP_MAIN_CLASS% ^
  --main-jar "%MAIN_JAR%" ^
  --java-options "-Dfile.encoding=UTF-8"
if errorlevel 1 goto :fail

echo [TurboTransfer] Compressing portable package...
powershell -NoProfile -Command "Compress-Archive -Path '%PORTABLE_DIR%\%APP_NAME%\*' -DestinationPath '%PORTABLE_ZIP%' -Force"
if errorlevel 1 goto :fail
echo [TurboTransfer] Portable package ready: %PORTABLE_ZIP%

REM ---- 4. Detect WiX (required only for the installer .exe) ----
call :detect_wix
if not defined WIX_BIN goto :no_wix
set "PATH=%WIX_BIN%;%PATH%"

echo [TurboTransfer] Building Windows installer with jpackage (WiX: %WIX_BIN%)...
jpackage ^
  --dest "%DIST_DIR%" ^
  --name "%APP_NAME%" ^
  --app-version "%APP_VERSION%" ^
  --vendor "%APP_VENDOR%" ^
  --input "%INPUT_DIR%" ^
  --main-class %DESKTOP_MAIN_CLASS% ^
  --main-jar "%MAIN_JAR%" ^
  --java-options "-Dfile.encoding=UTF-8" ^
  --win-dir-chooser ^
  --win-menu ^
  --win-shortcut ^
  --type exe
if errorlevel 1 goto :fail

if exist "%DIST_DIR%\%APP_NAME%-%APP_VERSION%.exe" ren "%DIST_DIR%\%APP_NAME%-%APP_VERSION%.exe" "%SETUP_EXE%"
echo [TurboTransfer] Installer ready: %DIST_DIR%\%SETUP_EXE%

goto :done

REM ====== subroutine: locate WiX 3.x (light.exe/candle.exe) ======
:detect_wix
set "WIX_BIN="
if defined WIX_HOME if exist "%WIX_HOME%\light.exe" set "WIX_BIN=%WIX_HOME%"
if not defined WIX_BIN if exist "%DEFAULT_WIX%\light.exe" set "WIX_BIN=%DEFAULT_WIX%"
if not defined WIX_BIN for %%I in (light.exe) do if not "%%~$PATH:I"=="" set "WIX_BIN=%%~dpI"
exit /b 0

:no_wix
echo [TurboTransfer] WiX not found - skipping installer (.exe); portable zip is ready.
echo [TurboTransfer] To build the installer, download WiX 3.14 binaries to
echo                  %DEFAULT_WIX% (or set WIX_HOME), then re-run this script.
goto :done

:done
rmdir /s /q "%INPUT_DIR%" 2>nul
echo [TurboTransfer] Done. Output under %DIST_DIR%.
goto :eof

:build_fail
echo [TurboTransfer] Build failed, packaging aborted. See %LAUNCHER_LOG%.
pause
exit /b 1

:missing
echo [TurboTransfer] Distribution files are missing. Run build-dist.bat first.
pause
exit /b 1

:locked
echo [TurboTransfer] Cannot clean %DIST_DIR% - files are locked. Close running app and retry.
pause
exit /b 1

:fail
echo [TurboTransfer] Packaging failed. See %LAUNCHER_LOG%.
pause
exit /b 1
