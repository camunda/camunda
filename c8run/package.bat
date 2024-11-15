@echo on
REM set constants
set CAMUNDA_VERSION=8.6.2
set CAMUNDA_CONNECTORS_VERSION=8.6.2
set ELASTICSEARCH_VERSION=8.13.4

set BASEDIR=%~dp0

REM Delete testing data before tar
rmdir /S /Q elasticsearch-%ELASTICSEARCH_VERSION%
rmdir /S /Q camunda-zeebe-%CAMUNDA_VERSION%
if exist log (
    del log\camunda.log
    del log\connectors.log
    del log\elasticsearch.log
)

REM Download and extract Elasticsearch if not already present
if not exist "elasticsearch-%ELASTICSEARCH_VERSION%.zip" (
    echo "Downloading Elasticsearch %ELASTICSEARCH_VERSION%"
    curl -sSL -o elasticsearch-%ELASTICSEARCH_VERSION%.zip "https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-%ELASTICSEARCH_VERSION%-windows-x86_64.zip"
    
    echo "Extracting Elasticsearch %ELASTICSEARCH_VERSION%"
    tar -xf elasticsearch-%ELASTICSEARCH_VERSION%.zip -C %BASEDIR%
) else (
  echo "Elasticsearch %ELASTICSEARCH_VERSION% already exists, skipping download."
)

REM Download and extract Camunda Zeebe if not already present
if not exist "camunda-zeebe-%CAMUNDA_VERSION%.zip" (
    echo "Downloading Camunda %CAMUNDA_VERSION%"
    curl -sSL -o camunda-zeebe-%CAMUNDA_VERSION%.zip "https://github.com/camunda/camunda/releases/download/%CAMUNDA_VERSION%/camunda-zeebe-%CAMUNDA_VERSION%.zip"

    echo "Extracting Camunda %CAMUNDA_VERSION%"
    tar -xf camunda-zeebe-%CAMUNDA_VERSION%.zip -C %BASEDIR%
) else (
  echo "Camunda %CAMUNDA_VERSION% already exists, skipping download."
)

REM Download Camunda Connectors if not already present
set connectorsFileName=connector-runtime-bundle-%CAMUNDA_CONNECTORS_VERSION%-with-dependencies.jar
if not exist "%connectorsFileName%" (
    echo "Downloading Camunda Connectors %CAMUNDA_CONNECTORS_VERSION%"
    curl -sSL -o "%connectorsFileName%" "https://repo1.maven.org/maven2/io/camunda/connector/connector-runtime-bundle/%CAMUNDA_CONNECTORS_VERSION%/%connectorsFileName%"
) else (
  echo "Camunda Connectors %CAMUNDA_CONNECTORS_VERSION% already exists, skipping download."
)

echo "Build c8run.exe"
go build -C windows -trimpath -o ..\c8run.exe

REM Create a tarball of the required files
echo "Creating tarball camunda8-run-%CAMUNDA_VERSION%-windows-x86_64.zip"
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

echo "Script completed successfully."
