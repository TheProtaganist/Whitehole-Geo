@echo off
REM Whitehole Geo - Advanced Launcher
REM This version includes additional options and better error handling

setlocal enabledelayedexpansion

REM Get the directory where this batch file is located
set "WHITEHOLE_DIR=%~dp0"
cd /d "%WHITEHOLE_DIR%"

REM Display header
echo ================================================================
echo Whitehole Geo - Mario Galaxy Level Editor with AI Commands
echo ================================================================
echo.

REM Check if Java is available and get version info
echo Checking Java installation...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java is not installed or not found in PATH
    echo.
    echo To fix this:
    echo 1. Download and install Java 11 or newer from https://adoptium.net/
    echo 2. Make sure Java is added to your system PATH
    echo 3. Restart this batch file
    echo.
    pause
    exit /b 1
)

REM Display Java version
echo [OK] Java found:
java -version 2>&1 | findstr "version"
echo.

REM Check if Whitehole.jar exists
if not exist "Whitehole.jar" (
    echo [ERROR] Whitehole.jar not found in:
    echo %WHITEHOLE_DIR%
    echo.
    echo Please make sure this batch file is in the same directory as Whitehole.jar
    echo.
    pause
    exit /b 1
)

echo [OK] Whitehole.jar found
echo.

REM Check for lib directory (dependencies)
if not exist "lib\" (
    echo [WARNING] lib directory not found - some features may not work
    echo.
) else (
    echo [OK] Dependencies found in lib directory
    echo.
)

REM UI Scale options
echo UI Scaling Options:
echo 1. Normal (1.0x) - Default
echo 2. Large (1.25x) - For high DPI displays
echo 3. Extra Large (1.5x) - For 4K displays
echo 4. Custom scale
echo 5. Use current setting
echo.
set /p "scale_choice=Choose UI scale (1-5) [default: 1]: "

REM Set UI scale based on choice
set "ui_scale=1.0"
if "%scale_choice%"=="2" set "ui_scale=1.25"
if "%scale_choice%"=="3" set "ui_scale=1.5"
if "%scale_choice%"=="4" (
    set /p "ui_scale=Enter custom scale factor (e.g., 1.2): "
)
if "%scale_choice%"=="5" set "ui_scale="

REM Memory options
echo.
echo Memory Options:
echo 1. Default - Let Java decide
echo 2. High Memory (2GB) - For large galaxies
echo 3. Maximum Memory (4GB) - For complex editing
echo.
set /p "memory_choice=Choose memory setting (1-3) [default: 1]: "

REM Set memory parameters
set "memory_params="
if "%memory_choice%"=="2" set "memory_params=-Xmx2G"
if "%memory_choice%"=="3" set "memory_params=-Xmx4G"

REM Build the command
set "java_cmd=java"
set "java_params=--add-exports=java.desktop/sun.awt=ALL-UNNAMED -Dsun.awt.noerasebackground=true"

if defined ui_scale (
    set "java_params=!java_params! -Dsun.java2d.uiScale=!ui_scale!"
)

if defined memory_params (
    set "java_params=!java_params! !memory_params!"
)

set "full_command=!java_cmd! !java_params! -jar Whitehole.jar"

echo.
echo ================================================================
echo Starting Whitehole Geo with AI Commands...
echo Command: !full_command!
echo Directory: %WHITEHOLE_DIR%
echo ================================================================
echo.

REM Run Whitehole
!full_command!

REM Check exit code
if %errorlevel% neq 0 (
    echo.
    echo ================================================================
    echo [ERROR] Whitehole exited with error code %errorlevel%
    echo ================================================================
    echo.
    echo Common solutions:
    echo - Make sure you have Java 11 or newer installed
    echo - Try running with different memory settings
    echo - Check that all files are present and not corrupted
    echo - Make sure you have a valid Super Mario Galaxy ROM directory
    echo.
    echo If problems persist, check the console output above for details.
    echo.
) else (
    echo.
    echo Whitehole closed normally.
)

pause
