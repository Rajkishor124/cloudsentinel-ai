@echo off
echo ============================================
echo  CloudSentinel AI - Starting Dev Services
echo ============================================
echo.
echo Starting 3 terminals:
echo   1. Spring Boot Backend (port 8080)
echo   2. React Frontend (port 3000)
echo   3. Python Agent (manual training)
echo.
echo NOTE: Each service opens in a new window.
echo Close windows to stop services.
echo ============================================
echo.

start "CloudSentinel Backend" cmd /k "cd /d "%~dp0..\backend" && echo Starting Spring Boot... && mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev"

start "CloudSentinel Frontend" cmd /k "cd /d "%~dp0..\frontend" && echo Starting Vite dev server... && npm run dev"

start "CloudSentinel Agent" cmd /k "cd /d "%~dp0..\agent" && echo Agent ready. Run: python scripts\train.py"
