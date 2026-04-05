@echo off
echo ============================================
echo  CloudSentinel AI - Development Setup
echo ============================================
echo.

echo [1/3] Setting up Python agent...
cd agent
py -m venv .venv
call .venv\Scripts\activate
pip install -r requirements.txt
cd ..
echo Python agent setup complete.
echo.

echo [2/3] Setting up frontend...
cd frontend
call npm install
cd ..
echo Frontend setup complete.
echo.

echo [3/3] Resolving backend dependencies...
cd backend
if not exist mvnw.cmd (
    echo Downloading Maven Wrapper...
    powershell -Command "Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar' -OutFile .mvn/wrapper/maven-wrapper.jar"
)
cd ..
echo Backend setup complete.
echo.

echo ============================================
echo  Setup Complete!
echo  Run 'scripts\run-dev.cmd' to start all services.
echo ============================================
