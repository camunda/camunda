#!/bin/sh

echo "starting Camunda Optimize with Elasticsearch";

PROGRAM="optimize"
BASEDIR=$(dirname "$0")

mkdir -p $BASEDIR/log
mkdir -p $BASEDIR/run

LOG_FILE=$BASEDIR/log/$PROGRAM.log
PID_FILE=$BASEDIR/run/$PROGRAM.pid

/bin/sh "$BASEDIR/server/elasticsearch-${elasticsearch.version}/bin/elasticsearch" --daemonize

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

echo -e "\nStaring jetty"
nohup java -Dpidfile=$PID_FILE -jar $BASEDIR/optimize-backend-${project.version}.jar </dev/null > $LOG_FILE 2>&1
