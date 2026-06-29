@echo off
setlocal

set "APP_NAME=TurboTransfer"
set "APP_STAGE_DIR=target\app"
set "LOG_DIR=log"
set "LAUNCHER_LOG_FILE=launcher.log"
set "MAIN_JAR=%APP_NAME%.jar"

set "APP_HOME=%~dp0"
if "%APP_HOME:~-1%"=="\" set "APP_HOME=%APP_HOME:~0,-1%"
cd /d "%APP_HOME%"
if not exist "%APP_HOME%\%LOG_DIR%" mkdir "%APP_HOME%\%LOG_DIR%"
set "LAUNCHER_LOG=%APP_HOME%\%LOG_DIR%\%LAUNCHER_LOG_FILE%"

echo [TurboTransfer] Compiling project...
echo [TurboTransfer] Compiling project... >> "%LAUNCHER_LOG%"
call mvn -q -DskipTests compile >> "%LAUNCHER_LOG%" 2>&1
if errorlevel 1 goto :fail

echo [TurboTransfer] Refreshing application package...
echo [TurboTransfer] Refreshing application package... >> "%LAUNCHER_LOG%"
if exist "%APP_STAGE_DIR%" rmdir /s /q "%APP_STAGE_DIR%"
if exist "%APP_STAGE_DIR%" goto :locked
mkdir "%APP_STAGE_DIR%"
mkdir "%APP_STAGE_DIR%\lib"

call mvn -q dependency:copy-dependencies "-DincludeScope=runtime" "-DoutputDirectory=%APP_STAGE_DIR%\lib" >> "%LAUNCHER_LOG%" 2>&1
if errorlevel 1 goto :fail

jar --create --file "%APP_STAGE_DIR%\%MAIN_JAR%" -C "target\classes" . >> "%LAUNCHER_LOG%" 2>&1
if errorlevel 1 goto :fail

if not exist "%APP_STAGE_DIR%\%MAIN_JAR%" goto :verify_fail
if not exist "%APP_STAGE_DIR%\lib\*.jar" goto :verify_fail

echo [TurboTransfer] Distribution is ready.
echo [TurboTransfer] Distribution is ready. >> "%LAUNCHER_LOG%"
exit /b 0

:locked
echo [TurboTransfer] Cannot clean %APP_STAGE_DIR% - files are locked by another process.
echo [TurboTransfer] Close IntelliJ IDEA / running TurboTransfer, then retry.
echo [TurboTransfer] Build aborted: locked files in %APP_STAGE_DIR%. >> "%LAUNCHER_LOG%"
exit /b 1

:verify_fail
echo [TurboTransfer] Build output incomplete (missing jar or dependency libs).
echo [TurboTransfer] Build output incomplete. >> "%LAUNCHER_LOG%"
exit /b 1

:fail
echo [TurboTransfer] Build failed. See %LAUNCHER_LOG% for details.
echo [TurboTransfer] Build failed. >> "%LAUNCHER_LOG%"
exit /b 1
