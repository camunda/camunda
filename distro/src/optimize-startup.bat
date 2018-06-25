@echo off

:: ###############################################################################
:: #                                                                             #
:: #                     Optimize Production Startup Script                      #
:: #                                                                             #
:: ###############################################################################
::
:: Note: This script is supposed to be used in production
:: to start-up Optimize. For further information please consult
:: the documentation: https://docs.camunda.org/optimize/latest/technical-guide/installation/

echo Setting up environment variables...

set BASEDIR=%~dp0
cd "%BASEDIR%"

if not exist ".\log" mkdir log

set LOG_FILE=%BASEDIR%log\optimize.log

IF DEFINED JAVA_HOME (
  set JAVA="%JAVA_HOME%\bin\java.exe"
) ELSE (
  set JAVA=java
)


:: Set up the optimize classpaths, i.e. add the environment folder, all jars in the
:: plugin directory and the optimize jar
set OPTIMIZE_CLASSPATH="%BASEDIR%environment;%BASEDIR%plugin\*;%BASEDIR%optimize-backend-${project.version}.jar"

echo.
echo Starting Camunda Optimize ${project.version}
echo.

:: start optimize
set OPTIMIZE_OUTPUT=echo Optimize has been started. Use CTRL + C to stop Optimize!
set OPTIMIZE_CMD=%JAVA% -cp %OPTIMIZE_CLASSPATH% -Dfile.encoding=UTF-8 org.camunda.optimize.Main
start "Camunda Optimize" cmd /c ^( %OPTIMIZE_OUTPUT% ^&^& %OPTIMIZE_CMD% ^> %LOG_FILE% ^2^>^&^1 ^)

echo Camunda Optimize has been started.
echo.