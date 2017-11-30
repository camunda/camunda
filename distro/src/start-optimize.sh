#!/bin/bash

trap "trap - SIGTERM && kill -- -$$" SIGINT SIGTERM EXIT
PROGRAM="optimize"
BASEDIR=$(dirname "$0")

mkdir -p $BASEDIR/log
mkdir -p $BASEDIR/run

LOG_FILE=$BASEDIR/log/$PROGRAM.log
PID_FILE=$BASEDIR/run/$PROGRAM.pid

if [[ $1 != "standalone" ]]; then
	echo "Starting Elasticsearch...";
	exec /bin/bash "$BASEDIR/server/elasticsearch-${elasticsearch.version}/bin/elasticsearch" &

    RETRIES=6
    SLEEP_TIME=10
    URL="http://localhost:9200/_cluster/health?wait_for_status=yellow&timeout=10s"
    COMMAND="curl -XGET $URL"

	until ${COMMAND};
      do
        sleep ${SLEEP_TIME}
        RETRIES=$(( RETRIES - 1 ))
      if [ $RETRIES = 0 ]; then
        echo "Error: elasticsearch did not start!"
        exit 1;
      else
        echo "Polling elasticsearch ... $RETRIES left"
      fi
    done
	echo "Elasticsearch has been successfully started!";
fi

echo "Starting Camunda Optimize...";

# Set up the optimize classpaths, i.e. add the environment folder, all jars in the
# plugin directory and the optimize jar
OPTIMIZE_CLASSPATH=$BASEDIR/environment:$BASEDIR/plugin/*:$BASEDIR/optimize-backend-${project.version}.jar

nohup java -cp $OPTIMIZE_CLASSPATH -Dpidfile=$PID_FILE -Dfile.encoding=UTF-8 org.camunda.optimize.Main </dev/null > $LOG_FILE 2>&1
