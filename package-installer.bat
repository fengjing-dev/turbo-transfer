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
set "APP_DIR=%INPUT_DIR%"

cd /d "%~dp0"
set "APP_HOME=%~dp0"
if "%APP_HOME:~-1%"=="\" set "APP_HOME=%APP_HOME:~0,-1%"
if not exist "%APP_HOME%\%LOG_DIR%" mkdir "%APP_HOME%\%LOG_DIR%"
set "LAUNCHER_LOG=%APP_HOME%\%LOG_DIR%\%LAUNCHER_LOG_FILE%"

echo [TurboTransfer] Building distribution before packaging... >> "%LAUNCHER_LOG%"
call build-dist.bat >> "%LAUNCHER_LOG%" 2>&1
if errorlevel 1 goto :build_fail

if not exist "%APP_STAGE_DIR%\%MAIN_JAR%" goto :missing
if not exist "%APP_STAGE_DIR%\lib" goto :missing

echo [TurboTransfer] Preparing installer layout...
if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"
mkdir "%APP_DIR%\lib"

xcopy /y "%APP_STAGE_DIR%\%MAIN_JAR%" "%APP_DIR%\" >nul
if errorlevel 1 goto :fail

xcopy /e /i /y "%APP_STAGE_DIR%\lib\*" "%APP_DIR%\lib\" >nul
if errorlevel 1 goto :fail

echo [TurboTransfer] Building Windows installer with jpackage...
echo [TurboTransfer] Building Windows installer with jpackage... >> "%LAUNCHER_LOG%"
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

echo [TurboTransfer] Windows installer is ready under %DIST_DIR%.
echo [TurboTransfer] Windows installer is ready under %DIST_DIR%. >> "%LAUNCHER_LOG%"
goto :eof

:build_fail
echo [TurboTransfer] Build failed, packaging aborted.
echo [TurboTransfer] Build failed, packaging aborted. >> "%LAUNCHER_LOG%"
pause
exit /b 1

:missing
echo [TurboTransfer] Distribution files are missing.
echo [TurboTransfer] Please run build-dist.bat first.
pause
exit /b 1

:fail
echo [TurboTransfer] Packaging failed.
pause
exit /b 1
