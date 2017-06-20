@echo off

echo Starting Camunda Optimize ${project.version} with Elasticsearch ${elasticsearch.version}

echo Setting up environment variables...

set PROGRAM=optimize
set BASEDIR=%~dp0
cd %BASEDIR%

if not exist ".\log" mkdir log
if not exist ".\run" mkdir run

set LOG_FILE=%BASEDIR%log\%PROGRAM%.log

set RETRIES=5
set SLEEP_TIME=10

set COMMAND=curl.exe -XGET http://localhost:9200/_cluster/health?wait_for_status=yellow^^^&timeout=10s

echo Environment is set up.

echo Starting Elasticsearch...
start %BASEDIR%\server\elasticsearch-${elasticsearch.version}\bin\elasticsearch.bat

:while1
:: query elasticsearch if it's up
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
echo Starting jetty...
java -Dfile.encoding=UTF-8 -jar %BASEDIR%optimize-backend-${project.version}.jar > %LOG_FILE% 2>&1
 