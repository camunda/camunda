#!/bin/bash

###############################################################################
#                                                                             #
#                     Optimize Demo Startup Script                            #
#                                                                             #
###############################################################################
#
# Note: This script is supposed to be used in a demo environment to
# play around with Optimize. This script starts Elasticsearch, waits for it,
# starts Optimize, waits for it and opens a browser tab with Optimize for you.
# For further information please consult
# the documentation: https://docs.camunda.org/optimize/${docs.version}/technical-guide/setup/installation/

function checkStartup {
	RETRIES=20
    SLEEP_TIME=20
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

# make sure to kill background/ child processes as well
trap 'trap - SIGTERM && kill -- -$$' SIGINT SIGTERM EXIT

cd $(dirname "$0")
BASEDIR=$(pwd)

mkdir -p "$BASEDIR/log"

# we need to set the JAVA_HOME environment variable so that ElasticSearch can
# use that one later on for the execution.
if [ -n "$JAVA_HOME" ]; then
  JAVA_PATH="$JAVA_HOME"
else
  JAVA_PATH=$(command -v java)
  # strip the "/bin/java" suffix so it's correctly resolved later on in the ElasticSearch script
  JAVA_PATH=${JAVA_PATH%"/bin/java"}
fi
# make the JAVA_HOME variable available for ElasticSearch
export ES_JAVA_HOME=${JAVA_PATH}

if [ ! -x "$ES_JAVA_HOME/bin/java" ]; then
  echo "Could not find any Java installation on your machine. Please make sure that Java is installed and the JAVA_HOME environment variable is set!" >&2
  exit 1
fi

# limit the java heapspace used by ElasticSearch to 1GB
export ES_JAVA_OPTS="-Xms1g -Xmx1g"

echo
echo "Starting Elasticsearch ${elasticsearch.demo.version}...";
echo "(Hint: you can find the log output in the 'elasticsearch.log' file in the 'log' folder of your distribution.)"
echo
ELASTICSEARCH_LOG_FILE=$BASEDIR/log/elasticsearch.log
bash "$BASEDIR/elasticsearch/elasticsearch-${elasticsearch.demo.version}/bin/elasticsearch" </dev/null > "$ELASTICSEARCH_LOG_FILE" 2>&1 &

URL="http://localhost:9200/_cluster/health?wait_for_status=green&wait_for_active_shards=all&wait_for_no_initializing_shards=true&timeout=120s"
checkStartup $URL "Elasticsearch"

echo
echo "Starting Camunda Optimize ${project.version}...";
echo "(Hint: you can find the log output in the 'optimize*.log' files in the 'log' folder of your distribution.)"
echo
OPTIMIZE_STARTUP_LOG_FILE=$BASEDIR/log/optimize-startup.log
SCRIPT_PATH="./optimize-startup.sh"
bash "$SCRIPT_PATH" "$@" </dev/null > "$OPTIMIZE_STARTUP_LOG_FILE" 2>&1 &

# Check if Optimize has been started
URL="http://localhost:8090"
checkStartup $URL "Camunda Optimize"

# Open Optimize in the browser
BROWSERS="gnome-www-browser x-www-browser firefox chromium chromium-browser google-chrome"

if [ -z "$BROWSER" ]; then
  for executable in $BROWSERS; do
    BROWSER=$(command -v "$executable" 2> /dev/null)
    if [ -n "$BROWSER" ]; then
      break;
    fi
  done
fi

if [ -z "$BROWSER" ]; then
  (sleep 10; echo -e "Sorry for the inconvenience, but it wasn't possible to locate your default browser... \nIf you want to see our default website please open your browser and insert this URL:\nhttp://localhost:8090/login";) &
else
  (sleep 2; $BROWSER "http://localhost:8090";) &
fi

# print some info for the user
echo
echo "You can now view Camunda Optimize in your browser."
echo
echo -e "\t http://localhost:8090"
echo

wait
