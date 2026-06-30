@echo off
setlocal

set "APP_NAME=TurboTransfer"
set "APP_VERSION=1.0.0"
set "APP_STAGE_DIR=target\app"
set "RUNTIME_DIR=target\jre"
set "LOG_DIR=log"
set "LAUNCHER_LOG_FILE=launcher.log"
set "MAIN_JAR=%APP_NAME%.jar"
set "DESKTOP_APP_DIR=desktop-app"

set "APP_HOME=%~dp0"
if "%APP_HOME:~-1%"=="\" set "APP_HOME=%APP_HOME:~0,-1%"
cd /d "%APP_HOME%"
if not exist "%APP_HOME%\%LOG_DIR%" mkdir "%APP_HOME%\%LOG_DIR%"
set "LAUNCHER_LOG=%APP_HOME%\%LOG_DIR%\%LAUNCHER_LOG_FILE%"

where jlink.exe >nul 2>nul
if errorlevel 1 (
  echo [TurboTransfer] JDK 17+ is required but jlink.exe was not found in PATH.
  echo [TurboTransfer] JDK 17+ is required but jlink.exe was not found in PATH. >> "%LAUNCHER_LOG%"
  goto :fail
)

where npm.cmd >nul 2>nul
if errorlevel 1 (
  echo [TurboTransfer] Node.js/npm is required but npm.cmd was not found in PATH.
  echo [TurboTransfer] Node.js/npm is required but npm.cmd was not found in PATH. >> "%LAUNCHER_LOG%"
  goto :fail
)

echo [TurboTransfer] Building Java backend distribution...
call "%APP_HOME%\build-dist.bat"
if errorlevel 1 goto :build_fail

if not exist "%APP_STAGE_DIR%\%MAIN_JAR%" goto :missing
if not exist "%APP_STAGE_DIR%\lib" goto :missing

echo [TurboTransfer] Preparing bundled Java runtime...
if exist "%RUNTIME_DIR%" rmdir /s /q "%RUNTIME_DIR%"
if exist "%RUNTIME_DIR%" goto :locked
jlink ^
  --add-modules java.base,java.desktop,java.logging,java.management,java.naming,jdk.crypto.ec,jdk.unsupported ^
  --strip-debug ^
  --no-header-files ^
  --no-man-pages ^
  --output "%RUNTIME_DIR%" >> "%LAUNCHER_LOG%" 2>&1
if errorlevel 1 goto :fail

echo [TurboTransfer] Checking Electron packaging dependencies...
if not exist "%DESKTOP_APP_DIR%\node_modules\electron-builder" (
  echo [TurboTransfer] electron-builder is missing. Run "npm install" in %DESKTOP_APP_DIR% first.
  echo [TurboTransfer] electron-builder is missing. >> "%LAUNCHER_LOG%"
  goto :fail
)

echo [TurboTransfer] Building Windows installer...
cd /d "%APP_HOME%\%DESKTOP_APP_DIR%"
call npm.cmd run dist:win >> "%LAUNCHER_LOG%" 2>&1
if errorlevel 1 goto :fail

cd /d "%APP_HOME%"
echo [TurboTransfer] Installer build complete. Output under dist.
echo [TurboTransfer] Installer build complete. >> "%LAUNCHER_LOG%"
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
echo [TurboTransfer] Cannot clean %RUNTIME_DIR% - files are locked. Close running app and retry.
pause
exit /b 1

:fail
cd /d "%APP_HOME%"
echo [TurboTransfer] Packaging failed. See %LAUNCHER_LOG%.
pause
exit /b 1
