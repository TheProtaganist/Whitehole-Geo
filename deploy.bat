@echo off
echo ================================================================
echo Whitehole Geo - GitHub Deployment Script
echo ================================================================
echo.

echo Checking if this is a git repository...
if not exist ".git" (
    echo This is not a git repository. Initializing...
    git init
    echo.
)

echo Setting up remote repository...
git remote remove origin 2>nul
git remote add origin https://github.com/TheProtaganist/Whitehole-Geo.git
echo.

echo Checking git status...
git status
echo.

echo Adding all files to git...
git add .
echo.

echo Creating commit...
set /p commit_message="Enter commit message (or press Enter for default): "
if "%commit_message%"=="" set commit_message=Major update: Added AI Commands system with enhanced camera controls

git commit -m "%commit_message%"
echo.

echo ================================================================
echo IMPORTANT: The next step will FORCE PUSH and OVERWRITE
echo all files in the GitHub repository!
echo ================================================================
echo.
set /p confirm="Are you sure you want to continue? This will delete all existing files on GitHub. (y/N): "

if /i "%confirm%"=="y" (
    echo.
    echo Force pushing to GitHub...
    echo This will replace ALL files in the repository!
    git push -f origin main
    echo.
    echo ================================================================
    echo Deployment complete!
    echo Repository URL: https://github.com/TheProtaganist/Whitehole-Geo
    echo ================================================================
) else (
    echo.
    echo Deployment cancelled. Files are staged and committed locally.
    echo You can push manually later with: git push -f origin main
)

echo.
pause
