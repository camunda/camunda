@echo off

:: ###############################################################################
:: #                                                                             #
:: #                     Elasticsearch Startup Script                            #
:: #                                                                             #
:: ###############################################################################
:: #
:: # Note: This script is supposed to be used in a demo environment to
:: # start Elasticsearch only without starting Optimize.
:: # For further information please consult
:: # the documentation: https://docs.camunda.org/optimize/${docs.version}/technical-guide/setup/installation/

echo Setting up environment variables...

set BASEDIR=%~dp0
cd "%BASEDIR%"

if not exist ".\log" mkdir log

set RETRIES=5
set SLEEP_TIME=10

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
echo The default Elasticsearch port is 9200
echo Elasticsearch instance will be terminated with termination of this script
