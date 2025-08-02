@echo off
REM Whitehole Geo - Mario Galaxy Level Editor with AI Commands
REM Batch file to run Whitehole with proper Java parameters

REM Check if Java is available
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Java is not installed or not found in PATH
    echo Please install Java 11 or newer to run Whitehole
    pause
    exit /b 1
)

REM Get the directory where this batch file is located
set "WHITEHOLE_DIR=%~dp0"

REM Change to the Whitehole directory
cd /d "%WHITEHOLE_DIR%"

REM Check if Whitehole.jar exists
if not exist "Whitehole.jar" (
    echo ERROR: Whitehole.jar not found in %WHITEHOLE_DIR%
    echo Please make sure this batch file is in the same directory as Whitehole.jar
    pause
    exit /b 1
)

echo Starting Whitehole Geo...
echo Directory: %WHITEHOLE_DIR%

REM Run Whitehole with the required Java parameters
REM --add-exports: Required for Java 11+ compatibility
REM -Dsun.java2d.uiScale=1.0: UI scaling (can be modified if needed)
REM -Dsun.awt.noerasebackground=true: Better graphics performance
java --add-exports=java.desktop/sun.awt=ALL-UNNAMED -Dsun.java2d.uiScale=1.0 -Dsun.awt.noerasebackground=true -jar Whitehole.jar

REM Check if the program exited with an error
if %errorlevel% neq 0 (
    echo.
    echo ERROR: Whitehole exited with error code %errorlevel%
    echo This might be due to:
    echo - Incompatible Java version (requires Java 11 or newer)
    echo - Missing dependencies
    echo - Corrupted Whitehole.jar file
    echo.
    pause
)
