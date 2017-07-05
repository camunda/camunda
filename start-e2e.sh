#!/bin/sh
echo "Starting E2E tests"
sh ./start-selenium.sh &

mvn -s settings.xml -Pproduction -DskipTests -Padd-license-key clean package

RETRIES=6
SLEEP_TIME=10
URL="http://localhost:8080"
COMMAND="curl -XGET $URL"

node ./client/scripts/engine.js start 8080 &

until ${COMMAND} > /dev/null;
  do
    sleep ${SLEEP_TIME}
    RETRIES=$(( RETRIES - 1 ))
  if [ $RETRIES = 0 ]; then
    echo "Error: engine did not start!"
    exit 1;
  else
    echo "Polling engine ... $RETRIES left"
  fi
done

mkdir ./distro/target/distro
tar -xzvf ./distro/target/*.tar.gz -C ./distro/target/distro

echo "********STARTING OPTIMIZE********"
./distro/target/distro/start-optimize.sh &

RETRIES=10
SLEEP_TIME=10
URL="http://localhost:8090"
COMMAND="curl -XGET $URL"

until ${COMMAND} > /dev/null;
  do
    sleep ${SLEEP_TIME}
    RETRIES=$(( RETRIES - 1 ))
  if [ $RETRIES = 0 ]; then
    echo "Error: jetty did not start!"
    exit 1;
  else
    echo "Polling jetty ... $RETRIES left"
  fi
done

./client/node_modules/.bin/wdio ./client/wdio-ci.conf.js
if [ $? -ne 0 ]; then exit 1; fi

pkill -f optimize-backend
pkill -f tomcat
pkill -f elasticsearch
pkill -f selenium-standalone
