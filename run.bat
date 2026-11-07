@echo off
rem ------------------------------------------------------------------
rem  Student Course Management System - compile and start (Windows)
rem  Needs a Java Development Kit (JDK), version 11 or newer.
rem ------------------------------------------------------------------
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
  echo Java was not found. Install a JDK 11 or newer, for example from https://adoptium.net
  echo then run this file again.
  pause
  exit /b 1
)

if not exist out mkdir out
echo Compiling...
javac -encoding UTF-8 -d out src\*.java
if errorlevel 1 (
  echo.
  echo Compiling failed. See the errors above.
  pause
  exit /b 1
)

java -cp out Main %*
pause
