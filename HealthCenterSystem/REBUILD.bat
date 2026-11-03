@echo off
setlocal
cd /d "%~dp0"
where javac >nul 2>&1
if errorlevel 1 (
  echo Java JDK was not found. Please install JDK 17 or 21 and add it to PATH.
  pause
  exit /b 1
)
if exist build rmdir /s /q build
mkdir build
javac -encoding UTF-8 -d build src\Main.java src\Authentication\*.java src\Components\*.java src\Data\*.java src\Patient\*.java src\QueueManager\*.java src\QueueSimulation\*.java src\Store\*.java
if errorlevel 1 (
  echo Source compilation failed. Check the errors displayed above.
  pause
  exit /b 1
)
jar cfe HealthCenterSystem.jar Main -C build .
if errorlevel 1 (
  echo Could not create runnable JAR file.
  pause
  exit /b 1
)
echo.
echo Build successful. Double-click RUN.bat to start.
pause
