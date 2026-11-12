@echo off
setlocal
cd /d "%~dp0"

echo ==============================================
echo   GawharaStructures - Build JAR
 echo ==============================================

where java >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Java was not found in PATH. Install JDK 25.
  pause
  exit /b 1
)

java -version 2>&1 | findstr /C:"version \"25" >nul
if errorlevel 1 (
  echo [ERROR] This project targets Paper 26.3 and requires JDK 25.
  echo Install JDK 25 and set JAVA_HOME/PATH, then run this file again.
  pause
  exit /b 1
)

where mvn >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Maven was not found in PATH. Install Maven 3.9 or newer.
  pause
  exit /b 1
)

echo [INFO] Building against Paper API 26.3.build.170-beta...
mvn --batch-mode --no-transfer-progress clean package
if errorlevel 1 (
  echo [ERROR] Build failed. See the Maven error above.
  pause
  exit /b 1
)

echo.
echo [SUCCESS] JAR created:
echo %CD%\target\GawharaStructures-1.0.1.jar
pause
