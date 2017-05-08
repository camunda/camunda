#!/bin/sh
echo "Starting E2E tests"
sh ./start-selenium.sh &

mvn clean package -Pproduction -DskipTests
mkdir ./distro/target/distro
tar -xzvf ./distro/target/*.tar.gz -C ./distro/target/distro
./distro/target/distro/start-optimize.sh &

RETRIES=6
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

node ./client/scripts/add_demo_data.js
./client/node_modules/.bin/wdio ./client/wdio-ci.conf.js
pkill -f optimize-backend

echo "Stopping E2E test"