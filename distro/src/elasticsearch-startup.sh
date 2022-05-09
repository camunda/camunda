#!/bin/bash

###############################################################################
#                                                                             #
#                     Elasticsearch Startup Script                            #
#                                                                             #
###############################################################################
#
# Note: This script is supposed to be used in a demo environment to
# start Elasticsearch only without starting Optimize.
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

echo "The default Elasticsearch port is 9200"
echo "Elasticsearch instance will be terminated with termination of this script"

wait

