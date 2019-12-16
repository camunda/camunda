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

# make sure to kill background/ child processes as well
trap 'trap - SIGTERM && kill -- -$$' SIGINT SIGTERM EXIT

BASEDIR=$(dirname "$0")
mkdir -p $BASEDIR/log

echo
echo "Starting Elasticsearch ${elasticsearch.version}...";
echo "(Hint: you can find the log output in the 'elasticsearch.log' file in the 'log' folder of your distribution.)"
echo
ELASTICSEARCH_LOG_FILE=$BASEDIR/log/elasticsearch.log
bash "$BASEDIR/elasticsearch/elasticsearch-${elasticsearch.version}/bin/elasticsearch" </dev/null > $ELASTICSEARCH_LOG_FILE 2>&1 &

URL="http://localhost:9200/_cluster/health?wait_for_status=yellow&timeout=10s"
checkStartup $URL "Elasticsearch"

echo
echo "Starting Camunda Optimize ${project.version}...";
echo "(Hint: you can find the log output in the 'optimize*.log' files in the 'log' folder of your distribution.)"
echo
SCRIPT_PATH="./optimize-startup.sh"
bash "$SCRIPT_PATH" $@ >/dev/null 2>&1 &

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
  (sleep 2; $BROWSER "http://localhost:8090/#/login";) &
fi

# print some info for the user
echo
echo "You can now view Camunda Optimize in your browser."
echo
echo -e "\t http://localhost:8090/login"
echo

wait
