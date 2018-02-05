#!/bin/bash

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
        echo "Polling $NAME ... $RETRIES left"
      fi
    done
	echo "$NAME has successfully been started.";
}

trap "trap - SIGTERM && kill -- -$$" SIGINT SIGTERM EXIT
PROGRAM="optimize"
BASEDIR=$(dirname "$0")

mkdir -p $BASEDIR/log
mkdir -p $BASEDIR/run

LOG_FILE=$BASEDIR/log/$PROGRAM.log
PID_FILE=$BASEDIR/run/$PROGRAM.pid

if [[ $1 != "standalone" ]]; then
    echo
	echo "Starting Elasticsearch...";
	echo
	exec /bin/bash "$BASEDIR/server/elasticsearch-${elasticsearch.version}/bin/elasticsearch" &

    URL="http://localhost:9200/_cluster/health?wait_for_status=yellow&timeout=10s"
	checkStartup $URL "Elasticsearch"
fi

echo
echo "Starting Camunda Optimize...";
echo

# Set up the optimize classpaths, i.e. add the environment folder, all jars in the
# plugin directory and the optimize jar
OPTIMIZE_CLASSPATH=$BASEDIR/environment:$BASEDIR/plugin/*:$BASEDIR/optimize-backend-${project.version}.jar

nohup java -cp $OPTIMIZE_CLASSPATH -Dpidfile=$PID_FILE -Dfile.encoding=UTF-8 org.camunda.optimize.Main </dev/null > $LOG_FILE 2>&1 &

# Open Optimize in the browser
BROWSERS="gnome-www-browser x-www-browser firefox chromium chromium-browser google-chrome"

if [ -z "$BROWSER" ]; then
  for executable in $BROWSERS; do
    BROWSER=`which $executable 2> /dev/null`
    if [ -n "$BROWSER" ]; then
      break;
    fi
  done
fi

if [ -z "$BROWSER" ]; then
  (sleep 15; echo -e "Sorry for the inconvenience, but it wasn't possible to locate your default browser... \nIf you want to see our default website please open your browser and insert this URL:\nhttp://localhost:8090/login";) &
else
  (sleep 5; $BROWSER "http://localhost:8090/login";) &
fi

# Check if Optimize has been started
URL="http://localhost:8090"
checkStartup $URL "Camunda Optimize"

# print some info for the user
echo
echo "You can now view Camunda Optimize in your browser."
echo
echo -e "\t http://localhost:8090/login"
echo


wait
