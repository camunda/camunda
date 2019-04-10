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

set RETRIES=5
set SLEEP_TIME=10

set COMMAND=curl.exe -f -XGET http://localhost:9200/_cluster/health?wait_for_status=yellow^^^&timeout=10s
:: we need to execute curl once, otherwise we don't get the correct error code
%COMMAND% >nul 2>&1

echo Environment is set up.

echo Starting Elasticsearch ${elasticsearch.version}...
start "Elasticsearch" call "%BASEDIR%elasticsearch\elasticsearch-${elasticsearch.version}\bin\elasticsearch.bat"

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
echo Starting Optimize ...
echo.
set LOG_FILE=%BASEDIR%log\optimize.log
set OPTIMIZE_OUTPUT=echo Optimize has been started. Use CTRL + C to stop Optimize!
start "Camunda Optimize" cmd /c ^( %OPTIMIZE_OUTPUT% ^&^& "optimize-startup.bat" %* ^> %LOG_FILE% ^2^>^&^1 ^)


:: command to query optimize
set COMMAND=curl.exe -f -XGET http://localhost:8090/login
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
start "" http://localhost:8090/login

:: print some info for the user
set TAB=^ ^ ^ ^ 
echo.
echo You can now view Camunda Optimize in your browser.
echo.
echo %TAB%http://localhost:8090/#/login
echo.

