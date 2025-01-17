@echo on
REM set constants
set CAMUNDA_VERSION=8.6.7
set CAMUNDA_CONNECTORS_VERSION=8.6.6
set ELASTICSEARCH_VERSION=8.13.4

set BASEDIR=%~dp0
echo BASEDIR=%BASEDIR%

if not defined JAVA_ARTIFACTS_USER (
    echo Error: JAVA_ARTIFACTS_USER env var not set or is empty.
    exit /b 1
)

if not defined JAVA_ARTIFACTS_PASSWORD (
    echo Error: JAVA_ARTIFACTS_PASSWORD env var is not set or is empty.
    exit /b 1
)

REM Delete testing data before tar
rmdir /S /Q elasticsearch-%ELASTICSEARCH_VERSION%
rmdir /S /Q camunda-zeebe-%CAMUNDA_VERSION%
del log\camunda.log
del log\connectors.log
del log\elasticsearch.log

REM Retrieve elasticsearch
if not exist "elasticsearch-%ELASTICSEARCH_VERSION%.zip" (
    curl -L -o elasticsearch-%ELASTICSEARCH_VERSION%.zip "https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-%ELASTICSEARCH_VERSION%-windows-x86_64.zip"
)
tar -xf elasticsearch-%ELASTICSEARCH_VERSION%.zip -C %BASEDIR%

if not exist "camunda-zeebe-%CAMUNDA_VERSION%.zip" (
    gh release download -R "camunda/camunda" "%CAMUNDA_VERSION%" -p "camunda-zeebe-%CAMUNDA_VERSION%.zip"
)
tar -xf camunda-zeebe-%CAMUNDA_VERSION%.zip -C %BASEDIR%

set connectorsFileName=connector-runtime-bundle-%CAMUNDA_CONNECTORS_VERSION%-with-dependencies.jar
if not exist "%connectorsFileName%" (
    curl -L --user "%JAVA_ARTIFACTS_USER%:%JAVA_ARTIFACTS_PASSWORD%" -o "%connectorsFileName%" "https://repository.nexus.camunda.cloud/content/groups/internal/io/camunda/connector/connector-runtime-bundle/%CAMUNDA_CONNECTORS_VERSION%/%connectorsFileName%"
)

go build -C windows -trimpath -o ..\c8run.exe

tar -a -cf camunda8-run-%CAMUNDA_VERSION%-windows-x86_64.zip ^
  -C ..\ ^
  c8run\README.md ^
  c8run\connectors-application.properties ^
  c8run\%connectorsFileName% ^
  c8run\elasticsearch-%ELASTICSEARCH_VERSION% ^
  c8run\custom_connectors ^
  c8run\configuration ^
  c8run\c8run.exe ^
  c8run\endpoints.txt ^
  c8run\log ^
  c8run\windows\c8run_windows.go ^
  c8run\windows\process_tree.go ^
  c8run\windows\go.mod ^
  c8run\windows\go.sum ^
  c8run\camunda-zeebe-%CAMUNDA_VERSION%

