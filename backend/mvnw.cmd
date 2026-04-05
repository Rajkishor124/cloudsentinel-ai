@REM ----------------------------------------------------------------------------
@REM  Maven Wrapper for Windows
@REM ----------------------------------------------------------------------------
@echo off
setlocal enabledelayedexpansion

@REM Change to the directory where this script lives
cd /d "%~dp0"

set "WRAPPER_JAR=.mvn\wrapper\maven-wrapper.jar"
set "WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain"

@REM Check if wrapper jar exists
if not exist "%WRAPPER_JAR%" (
    echo Error: Maven wrapper JAR not found.
    echo Download it to .mvn\wrapper\maven-wrapper.jar from:
    echo https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar
    exit /b 1
)

@REM Find Java
if "%JAVA_HOME%"=="" (
    set "JAVA_EXE=java"
) else (
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
)

@REM Run Maven with current directory as project base
"%JAVA_EXE%" -Dmaven.multiModuleProjectDirectory=. -cp "%WRAPPER_JAR%" %WRAPPER_LAUNCHER% %*
exit /b %ERRORLEVEL%
