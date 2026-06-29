@echo off
setlocal

set "APP_NAME=TurboTransfer"
set "APP_STAGE_DIR=target\app"
set "LOG_DIR=log"
set "LAUNCHER_LOG_FILE=launcher.log"
set "MAIN_JAR=%APP_NAME%.jar"
set "SERVER_MAIN_CLASS=com.fatina.transfer.server.NettyUploadServer"

cd /d "%~dp0"
set "APP_HOME=%~dp0"
if "%APP_HOME:~-1%"=="\" set "APP_HOME=%APP_HOME:~0,-1%"
if not exist "%APP_HOME%\%LOG_DIR%" mkdir "%APP_HOME%\%LOG_DIR%"
set "LAUNCHER_LOG=%APP_HOME%\%LOG_DIR%\%LAUNCHER_LOG_FILE%"

call build-dist.bat >> "%LAUNCHER_LOG%" 2>&1
if errorlevel 1 (
    echo [TurboTransfer] Build step failed, trying existing distribution...
    echo [TurboTransfer] Build step failed, trying existing distribution... >> "%LAUNCHER_LOG%"
)

if not exist "%APP_STAGE_DIR%\%MAIN_JAR%" goto :missing
if not exist "%APP_STAGE_DIR%\lib" goto :missing

echo [TurboTransfer] Launching server mode...
echo [TurboTransfer] Launching server mode... >> "%LAUNCHER_LOG%"
java -Dturbo.transfer.app.home="%APP_HOME%" -cp "%APP_STAGE_DIR%\%MAIN_JAR%;%APP_STAGE_DIR%\lib\*" %SERVER_MAIN_CLASS% >> "%LAUNCHER_LOG%" 2>&1
goto :eof

:missing
echo [TurboTransfer] Distribution files are missing and build did not succeed.
pause
exit /b 1
