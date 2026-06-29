@echo off
setlocal

set "APP_NAME=TurboTransfer"
set "APP_STAGE_DIR=target\app"
set "LOG_DIR=log"
set "LAUNCHER_LOG_FILE=launcher.log"
set "MAIN_JAR=%APP_NAME%.jar"

cd /d "%~dp0"
set "APP_HOME=%~dp0"
if "%APP_HOME:~-1%"=="\" set "APP_HOME=%APP_HOME:~0,-1%"
if not exist "%APP_HOME%\%LOG_DIR%" mkdir "%APP_HOME%\%LOG_DIR%"
set "LAUNCHER_LOG=%APP_HOME%\%LOG_DIR%\%LAUNCHER_LOG_FILE%"

echo [TurboTransfer] Compiling project... >> "%LAUNCHER_LOG%"
call mvn -q -DskipTests compile >> "%LAUNCHER_LOG%" 2>&1
if errorlevel 1 goto :fail

echo [TurboTransfer] Refreshing application package... >> "%LAUNCHER_LOG%"
if exist "%APP_STAGE_DIR%" rmdir /s /q "%APP_STAGE_DIR%"
mkdir "%APP_STAGE_DIR%"
mkdir "%APP_STAGE_DIR%\lib"
call mvn -q dependency:copy-dependencies "-DincludeScope=runtime" "-DoutputDirectory=%APP_STAGE_DIR%\\lib" >> "%LAUNCHER_LOG%" 2>&1
if errorlevel 1 goto :fail

jar --create --file "%APP_STAGE_DIR%\%MAIN_JAR%" -C "target\classes" . >> "%LAUNCHER_LOG%" 2>&1
if errorlevel 1 goto :fail

echo [TurboTransfer] Distribution is ready.
echo [TurboTransfer] Desktop launcher: start-desktop.bat
echo [TurboTransfer] Server launcher : start-server.bat
exit /b 0
goto :eof

:fail
echo [TurboTransfer] Build failed.
echo [TurboTransfer] Build failed. >> "%LAUNCHER_LOG%"
exit /b 1
