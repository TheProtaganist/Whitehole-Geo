@echo off
REM Whitehole Build Script
REM Compiles and packages Whitehole from source

setlocal enabledelayedexpansion

echo ================================================================
echo Building Whitehole Geo...
echo ================================================================

REM Get the directory where this batch file is located
set "BUILD_DIR=%~dp0"
cd /d "%BUILD_DIR%"

REM Check if Java is available
echo Checking Java installation...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java is not installed or not found in PATH
    echo Please install Java 11 or newer
    pause
    exit /b 1
)

REM Check if javac is available
javac -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java compiler (javac) is not available
    echo Please install a Java Development Kit (JDK)
    pause
    exit /b 1
)

echo [OK] Java development tools found
echo.

REM Create build directories
echo Creating build directories...
if not exist "build" mkdir build
if not exist "build\classes" mkdir build\classes

REM Clean previous build
echo Cleaning previous build...
if exist "build\classes\*" del /q /s "build\classes\*" >nul 2>&1
for /d %%d in ("build\classes\*") do rd /s /q "%%d" >nul 2>&1

REM Compile Java sources
echo Compiling Java sources...
javac -cp "lib/*" -d build/classes -sourcepath src src/whitehole/Whitehole.java
if %errorlevel% neq 0 (
    echo [ERROR] Compilation failed
    pause
    exit /b 1
)

echo [OK] Compilation successful
echo.

REM Copy resources
echo Copying resources...
if exist "src\res" xcopy /e /i /y "src\res" "build\classes\res" >nul
echo [OK] Resources copied
echo.

REM Create JAR file
echo Creating JAR file...
if exist "Whitehole.jar" del "Whitehole.jar"
jar cfm Whitehole.jar manifest.mf -C build/classes .
if %errorlevel% neq 0 (
    echo [ERROR] JAR creation failed
    pause
    exit /b 1
)

echo [OK] JAR file created successfully
echo.

REM Display file info
for %%f in (Whitehole.jar) do (
    echo JAR file size: %%~zf bytes
)

echo ================================================================
echo Build completed successfully!
echo You can now run Whitehole.bat to start the application
echo ================================================================
echo.

pause
