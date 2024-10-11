@echo on
REM set constants
set CAMUNDA_VERSION=8.6.2
set CAMUNDA_CONNECTORS_VERSION=8.6.2
set ELASTICSEARCH_VERSION=8.13.4

set BASEDIR=%~dp0
echo BASEDIR=%BASEDIR%

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
    curl -L -o camunda-zeebe-%CAMUNDA_VERSION%.zip "https://github.com/camunda/camunda/releases/download/%CAMUNDA_VERSION%/camunda-zeebe-%CAMUNDA_VERSION%.zip"
)
tar -xf camunda-zeebe-%CAMUNDA_VERSION%.zip -C %BASEDIR%

set connectorsFileName=connector-runtime-bundle-%CAMUNDA_CONNECTORS_VERSION%-with-dependencies.jar
if not exist "%connectorsFileName%" (
    curl -L -o "%connectorsFileName%" "https://repo1.maven.org/maven2/io/camunda/connector/connector-runtime-bundle/%CAMUNDA_CONNECTORS_VERSION%/%connectorsFileName%"
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

