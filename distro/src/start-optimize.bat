@echo off

echo Setting up environment variables...

set ARGUMENT=%1
set PROGRAM=optimize
set BASEDIR=%~dp0
cd "%BASEDIR%"

if not exist ".\log" mkdir log
if not exist ".\run" mkdir run

set LOG_FILE=%BASEDIR%log\%PROGRAM%.log

set RETRIES=5
set SLEEP_TIME=10

set COMMAND=curl.exe -f -XGET http://localhost:9200/_cluster/health?wait_for_status=yellow^^^&timeout=10s
:: we need to execute curl once, otherwise we don't get the correct error code
%COMMAND% >nul 2>&1

echo Environment is set up.

if "%ARGUMENT%" neq "standalone" (

    echo Starting Elasticsearch ${elasticsearch.version}...
    start "" call "%BASEDIR%server\elasticsearch-${elasticsearch.version}\bin\elasticsearch.bat"

    :: query elasticsearch if it's up
    :while1
    %COMMAND% >nul 2>&1
    :: if there was an error wait and retry
    if %ERRORLEVEL% neq 0 (
        echo Polling elasticsearch ... %RETRIES% retries left
        timeout /t %SLEEP_TIME% /nobreak >nul
        set /a RETRIES-=1
        if %RETRIES% leq 0 (
            echo Error: Elasticsearch did not start!
            exit /b
        )
        goto :while1
    )
    echo Elasticsearch has successfully been started.
)

:: Set up the optimize classpaths, i.e. add the environment folder, all jars in the
:: plugin directory and the optimize jar
set OPTIMIZE_CLASSPATH="%BASEDIR%environment;%BASEDIR%plugin\*;%BASEDIR%optimize-backend-${project.version}.jar"

echo Starting Camunda Optimize ${project.version}

IF DEFINED JAVA_HOME (
  set JAVA="%JAVA_HOME%\bin\java.exe"
) ELSE (
  set JAVA=java
)

%JAVA% -cp %OPTIMIZE_CLASSPATH% -Dfile.encoding=UTF-8 org.camunda.optimize.Main > %LOG_FILE% 2>&1

