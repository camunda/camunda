@echo off

:: ###############################################################################
:: #                                                                             #
:: #                     Optimize Demo Startup Script                            #
:: #                                                                             #
:: ###############################################################################
::
:: Note: This script is supposed to be used in a demo environment to
:: play around with Optimize. This script starts Elasticsearch, waits for it,
:: starts Optimize, waits for it and opens a browser tab with Optimize for you.
:: For further information please consult
:: the documentation: https://docs.camunda.org/optimize/${docs.version}/technical-guide/setup/installation/

echo Setting up environment variables...

set BASEDIR=%~dp0
cd "%BASEDIR%"

if not exist ".\log" mkdir log

set RETRIES=20
set SLEEP_TIME=20

set COMMAND=curl.exe -f -XGET http://localhost:9200/_cluster/health?wait_for_status=green^^^&wait_for_active_shards=all^^^&wait_for_no_initializing_shards=true^^^&timeout=120s
:: we need to execute curl once, otherwise we don't get the correct error code
%COMMAND% >nul 2>&1

echo Environment is set up.

:: limit the java heapspace used by ElasticSearch to 1GB
set "ES_JAVA_OPTS=-Xms1g -Xmx1g"

echo Starting Elasticsearch ${elasticsearch.demo.version}...
echo (Hint: you can find the log output in the 'elasticsearch.log' file in the 'log' folder of your distribution.)
echo.
set ELASTICSEARCH_LOG_FILE=%BASEDIR%/log/elasticsearch.log
start /b "Elasticsearch" cmd /c "%BASEDIR%elasticsearch\elasticsearch-${elasticsearch.demo.version}\bin\elasticsearch.bat" >"%ELASTICSEARCH_LOG_FILE%" 2>&1

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

:: start optimize
echo.
echo Starting Optimize ${project.version}...
echo (Hint: you can find the log output in the 'optimize*.log' files in the 'log' folder of your distribution.)
echo.
set OPTIMIZE_STARTUP_LOG_FILE=%BASEDIR%/log/optimize-startup.log
start /b "Camunda Optimize" cmd /c "%BASEDIR%\optimize-startup.bat" %* >"%OPTIMIZE_STARTUP_LOG_FILE%" 2>&1


:: command to query optimize
set COMMAND=curl.exe -f -XGET http://localhost:8090
:: query Optimize if it's up
set RETRIES=5
:while2
%COMMAND% >nul 2>&1
:: if there was an error wait and retry
if %ERRORLEVEL% neq 0 (
    echo Polling Optimize ... %RETRIES% retries left
    timeout /t %SLEEP_TIME% /nobreak >nul
    set /a RETRIES-=1
    if %RETRIES% leq 0 (
        echo Error: Optimize did not start!
        exit /b
    )
    goto :while2
)
echo Optimize has successfully been started.

:: open Optimize in the browser
start "" http://localhost:8090

:: print some info for the user
set TAB=^ ^ ^ ^ 
echo.
echo You can now view Camunda Optimize in your browser.
echo.
echo %TAB%http://localhost:8090
echo.

