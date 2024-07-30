

@echo off

rem Set constants
set CAMUNDA_VERSION=8.6.0-alpha3
set CAMUNDA_CONNECTORS_VERSION=8.6.0-alpha3
set ELASTICSEARCH_VERSION=8.13.4
set EXPECTED_JAVA_VERSION=21

set SUBCOMMAND=%~1
rem Set BASEDIR to the directory of the currently executed script
set BASEDIR=%~dp0

rem Remove trailing backslash if it exists
set BASEDIR=%BASEDIR:~0,-1%

rem Navigate to the parent directory and get its path
pushd "%BASEDIR%\.."
set PARENTDIR=%cd%
popd

rem Set Paths
set DEPLOYMENT_DIR=%PARENTDIR%\configuration\resources
set WEBAPPS_PATH=%BASEDIR%\webapps\
set REST_PATH=%BASEDIR%\rest\
set SWAGGER_PATH=%BASEDIR%\swaggerui
set EXAMPLE_PATH=%BASEDIR%\example
set PID_PATH=%PARENTDIR%\run.pid
set ELASTIC_PID_PATH=%PARENTDIR%\elasticsearch.pid
set CONNECTORS_PID_PATH=%PARENTDIR%\connectors.pid

rem Define the OPTIONS_HELP variable
set OPTIONS_HELP=Options:^&echo.
set OPTIONS_HELP=%OPTIONS_HELP%   --webapps    - Enables the Camunda Platform Webapps^&echo.
set OPTIONS_HELP=%OPTIONS_HELP%   --rest       - Enables the REST API^&echo.
set OPTIONS_HELP=%OPTIONS_HELP%   --swaggerui  - Enables the Swagger UI^&echo.
set OPTIONS_HELP=%OPTIONS_HELP%   --example    - Enables the example application^&echo.
set OPTIONS_HELP=%OPTIONS_HELP%   --config     - Applies the specified configuration file^&echo.
set OPTIONS_HELP=%OPTIONS_HELP%   --detached   - Starts Camunda Run as a detached process^&echo.
@REM Configuration file defaults overriden because upstream config doesn't export to elasticsearch
setx ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME "io.camunda.zeebe.exporter.ElasticsearchExporter"
setx ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL "http://localhost:9200"
setx ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_INDEX_PREFIX "zeebe-record"

if %SUBCOMMAND% == "start" (


:checkStartup
set RETRIES=12
set SLEEP_TIME=10
set URL=%~1
set NAME=%~2
set COMMAND="curl -XGET %URL%"

:loop
%COMMAND%
set commandErrorLevel=%ERROR_LEVEL%
timeout /NOBREAK %SLEEP_TIME%
set RETRIES=%RETRIES%-1

if %RETRIES% == 0 (
  echo "Error: %NAME% did not start!"
  EXIT /B 1
)
echo "Polling %NAME% ... %RETRIES% retries left"

if %commandErrorLevel% NEQ 0 (
  goto loop
)

echo "%NAME% has successfully been started."

EXIT /B 0

set URL="http://localhost:9200/_cluster/health?wait_for_status=green&wait_for_active_shards=all&wait_for_no_initializing_shards=true&timeout=120s"
start /B checkStartup "%URL%" "Elasticsearch"

cd .\camunda-zeebe-%CAMUNDA_VERSION%
start "camunda8core" .\camunda-zeebe-%CAMUNDA_VERSION%\bin\camunda.bat >> %PARENTDIR%\log\camunda.log 2>&1
for /F "tokens=2 delims=," %%i in ('tasklist /FI "WINDOWNAME eq camunda8core" /FO CSV /NH') do (
    set PID=%%i
)
cd ..

start "camunda8connectors" %JAVA% -cp "%PARENTDIR%*:%PARENTDIR%\custom_connectors\*:.\camunda-zeebe-%CAMUNDA_VERSION%\lib\*" "io.camunda.connector.runtime.app.ConnectorRuntimeApplication" --spring.config.location=.\connectors-application.properties >> $PARENTDIR\log\connectors.log 2>&1
for /F "tokens=2 delims=," %%i in ('tasklist /FI "WINDOWNAME eq camunda8connectors" /FO CSV /NH') do (
    set PID=%%i
)


)

if %SUBCOMMAND% == "stop" (

  if exist "%PID_PATH%" (
    rem stop Camunda Run if the process is still running
    for /F "tokens=*" %%i in (%PID_PATH%) do (
      taskkill /F /PID %%i
    )

    rem remove process ID file
    del "%PID_PATH%"

    echo Camunda Run is shutting down.
  ) else (
    echo There is no instance of Camunda Run to shut down.
  )

  if exist "%ELASTIC_PID_PATH%" (
    rem stop Elasticsearch if the process is still running
    for /F "tokens=*" %%i in (%ELASTIC_PID_PATH%) do (
      taskkill /F /PID %%i
    )

    rem remove process ID file
    del "%ELASTIC_PID_PATH%"

    echo Elasticsearch is shutting down.
  ) else (
    echo There is no instance of Elasticsearch to shut down.
  )

  if exist "%CONNECTORS_PID_PATH%" (
    for /F "tokens=*" %%i in (%CONNECTORS_PID_PATH%) do (
      taskkill /F /PID %%i
    )
    del "%CONNECTORS_PID_PATH%"
    echo Connectors is shutting down.
  ) else (
    echo There is no instance of Connectors to shut down.
  )


)

if %SUBCOMMAND% == "help" (
rem Check if the first argument is empty or equals "help"
if "%1"=="" (
    echo Usage: run.bat [start^|stop] (options...)
    echo %OPTIONS_HELP%
) else if "%1"=="help" (
    echo Usage: run.bat [start^|stop] (options...)
    echo %OPTIONS_HELP%
)
