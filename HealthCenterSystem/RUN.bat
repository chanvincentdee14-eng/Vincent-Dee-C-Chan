@echo off
setlocal
cd /d "%~dp0"
where java >nul 2>&1
if errorlevel 1 (
    echo Java is not installed or is missing from PATH.
    echo Install a JDK 8 or newer and try again.
    pause
    exit /b 1
)
java -jar "%~dp0HealthCenterSystem.jar"
if errorlevel 1 (
    echo.
    echo An error occurred while running the app.
    pause
)
