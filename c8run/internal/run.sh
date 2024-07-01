#!/bin/bash

# set constants
CAMUNDA_VERSION="8.6.0-alpha3-rc3"
ELASTICSEARCH_VERSION="8.13.4"
EXPECTED_JAVA_VERSION=21

BASEDIR=$(dirname "$0")
PARENTDIR=$(builtin cd "$BASEDIR/.."; pwd)
DEPLOYMENT_DIR=$PARENTDIR/configuration/resources
WEBAPPS_PATH=$BASEDIR/webapps/
REST_PATH=$BASEDIR/rest/
SWAGGER_PATH=$BASEDIR/swaggerui
EXAMPLE_PATH=$BASEDIR/example
PID_PATH=$BASEDIR/run.pid
ELASTIC_PID_PATH=$BASEDIR/elasticsearch.pid
OPTIONS_HELP="Options:
  --webapps    - Enables the Camunda Platform Webapps
  --rest       - Enables the REST API
  --swaggerui  - Enables the Swagger UI
  --example    - Enables the example application
  --production - Applies the production.yaml configuration file
  --detached   - Starts Camunda Run as a detached process
"

architecture="$(uname -m)"

unameOut="$(uname -s)"
case "${unameOut}" in
    Linux*)     machine=Linux;;
    Darwin*)    machine=Mac;;
    *)          machine="UNKNOWN:${unameOut}"
esac

if [ "$machine" == "Mac" ]; then
    export PLATFORM=darwin
elif [ "$(expr substr $(uname -s) 1 5)" == "Linux" ]; then
    export PLATFORM=linux
fi


# set environment parameters
optionalComponentChosen=false
restChosen=false
swaggeruiChosen=false
productionChosen=false
detachProcess=false
classPath=$PARENTDIR/configuration/userlib/,$PARENTDIR/configuration/keystore/
configuration=$PARENTDIR/configuration/default.yml


if [ "$1" = "start" ] ; then
  shift
  # setup the JVM
  if [ "x$JAVA" = "x" ]; then
    if [ "x$JAVA_HOME" != "x" ]; then
      echo Setting JAVA property to "$JAVA_HOME/bin/java"
      JAVA="$JAVA_HOME/bin/java"
    else
      echo JAVA_HOME is not set. Unexpected results may occur.
      echo Set JAVA_HOME to the directory of your local JDK to avoid this message.
      JAVA="java"
    fi
  fi

  JAVA_VERSION=$("$JAVA" -version 2>&1 | head -1 | cut -d'"' -f2 | sed '/^0\./s///' | cut -d'.' -f1)
  echo Java version is $("$JAVA" -version 2>&1 | head -1 | cut -d'"' -f2)
  if [[ "$JAVA_VERSION" -lt "$EXPECTED_JAVA_VERSION" ]]; then
    echo "You must use at least JDK $EXPECTED_JAVA_VERSION to start Camunda Platform Run."
    exit 1
  fi

  if [ "x$JAVA_OPTS" != "x" ]; then
    echo JAVA_OPTS: $JAVA_OPTS
  fi

  # inspect arguments
  while [ "$1" != "" ]; do
    case $1 in
      --webapps )    optionalComponentChosen=true
                     classPath=$WEBAPPS_PATH,$classPath
                     echo WebApps enabled
                     ;;
      --rest )       optionalComponentChosen=true
                     restChosen=true
                     classPath=$REST_PATH,$classPath
                     echo REST API enabled
                     ;;
      --swaggerui )  optionalComponentChosen=true
                     swaggeruiChosen=true
                     classPath=$SWAGGER_PATH,$classPath
                     echo Swagger UI enabled
                     ;;
      --example )    optionalComponentChosen=true
                     classPath=$EXAMPLE_PATH,$classPath
                     echo Invoice Example included - needs to be enabled in application configuration as well
                     ;;
      --production ) configuration=$PARENTDIR/configuration/production.yml
                     productionChosen=true
                     ;;
      # the background flag shouldn't influence the optional component flags
      --detached )   detachProcess=true
                     echo Camunda Run will start in the background. Use the shutdown.sh script to stop it
                     ;;
      --help )       printf "%s" "$OPTIONS_HELP"
                     exit 0
                     ;;
      * )            exit 1
    esac
    shift
  done


  # Retrieve elasticsearch
  mkdir -p "$PARENTDIR/log"
  if [ ! -d "elasticsearch-$ELASTICSEARCH_VERSION" ]; then
    wget "https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-${ELASTICSEARCH_VERSION}-${PLATFORM}-${architecture}.tar.gz"
    tar -xzvf elasticsearch-${ELASTICSEARCH_VERSION}-${PLATFORM}-${architecture}.tar.gz
  fi

  if [ ! -d "camunda-zeebe-$CAMUNDA_VERSION" ]; then
    wget "https://github.com/camunda/camunda/releases/download/untagged-cc17819bc8c11c9bd503/camunda-zeebe-$CAMUNDA_VERSION.tar.gz"
    tar -xzvf camunda-zeebe-$CAMUNDA_VERSION.tar.gz
  fi

  # limit the java heapspace used by ElasticSearch to 1GB
  export ES_JAVA_OPTS="-Xms1g -Xmx1g"

  echo
  echo "Starting Elasticsearch ${ELASTICSEARCH_VERSION}...";
  echo "(Hint: you can find the log output in the 'elasticsearch.log' file in the 'log' folder of your distribution.)"
  echo
  ELASTICSEARCH_LOG_FILE=$PARENTDIR/log/elasticsearch.log
  bash -c "$PARENTDIR/elasticsearch-${ELASTICSEARCH_VERSION}/bin/elasticsearch -E xpack.ml.enabled=false -E xpack.security.enabled=false" </dev/null > "$ELASTICSEARCH_LOG_FILE" 2>&1 &
  echo $! > $ELASTIC_PID_PATH


  function checkStartup {
    RETRIES=6
      SLEEP_TIME=10
      URL=$1
      NAME=$2
      COMMAND="curl -XGET $URL"

    until ${COMMAND}&>/dev/null;
        do
          sleep ${SLEEP_TIME}
          RETRIES=$(( RETRIES - 1 ))
        if [ $RETRIES = 0 ]; then
          echo "Error: $NAME did not start!"
          exit 1;
        else
          echo "Polling $NAME ... $RETRIES retries left"
        fi
      done
    echo "$NAME has successfully been started.";
  }

  URL="http://localhost:9200/_cluster/health?wait_for_status=green&wait_for_active_shards=all&wait_for_no_initializing_shards=true&timeout=120s"
  checkStartup $URL "Elasticsearch"

  # If no optional component is chosen, enable REST and Webapps.
  # If production mode is not chosen, also enable Swagger UI and the example application.
  if [ "$optionalComponentChosen" = "false" ]; then
    restChosen=true
    echo REST API enabled
    echo WebApps enabled
    if [ "$productionChosen" = "false" ]; then
      swaggeruiChosen=true
      echo Swagger UI enabled
      echo Invoice Example included - needs to be enabled in application configuration as well
      classPath=$SWAGGER_PATH,$EXAMPLE_PATH,$classPath
    fi
    classPath=$WEBAPPS_PATH,$REST_PATH,$classPath
  fi

  # if Swagger UI is enabled but REST is not, warn the user
  if [ "$swaggeruiChosen" = "true" ] && [ "$restChosen" = "false" ]; then
    echo You did not enable the REST API. Swagger UI will not be able to send any requests to this Camunda Platform Run instance.
  fi

  echo classpath: $classPath

  # start the application
  if [ "$detachProcess" = "true" ]; then

    # check if a Camunda Run instance is already in operation
    if [ -s "$PID_PATH" ]; then
      echo "
A Camunda Run instance is already in operation (process id $(cat $PID_PATH)).

Please stop it or remove the file $PID_PATH."
      exit 1
    fi

    pushd $PARENTDIR/camunda-zeebe-$CAMUNDA_VERSION/
    ./bin/camunda >> $PARENTDIR/log/camunda.log 2>> $PARENTDIR/log/camunda.log &
    echo $! > "$PID_PATH"
    popd

  else
    pushd $PARENTDIR/camunda-zeebe-$CAMUNDA_VERSION/
    ./bin/camunda |& tee $PARENTDIR/log/camunda.log
    popd
  fi

elif [ "$1" = "stop" ] ; then

  if [ -s "$PID_PATH" ]; then
    # stop Camunda Run if the process is still running
    kill $(cat "$PID_PATH")

    # remove process ID file
    rm "$PID_PATH"

    echo "Camunda Run is shutting down."
  else
    echo "There is no instance of Camunda Run to shut down."
  fi
  if [ -s "$ELASTIC_PID_PATH" ]; then
    # stop Camunda Run if the process is still running
    kill $(cat "$ELASTIC_PID_PATH")

    # remove process ID file
    rm "$ELASTIC_PID_PATH"

    echo "Elasticsearch is shutting down."
  else
    echo "There is no instance of Elasticsearch to shut down."
  fi

elif [ "$1" = "" ] || [ "$1" = "help" ] ; then

  printf "Usage: run.sh [start|stop] (options...) \n%s" "$OPTIONS_HELP"
fi
