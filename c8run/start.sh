#!/bin/bash

BASEDIR=$(dirname "$0")
runScript=$BASEDIR/internal/run.sh


function checkCamundaStartup {
  RETRIES=24
    SLEEP_TIME=14
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


if [ $# -eq 0 ]; then

  # open a browser (must be done first)
  UNAME=`which uname`
  if [ -n "$UNAME" -a "`$UNAME`" = "Darwin" ]
  then
    BROWSERS="open"
  else
    BROWSERS="xdg-open gnome-www-browser x-www-browser firefox chromium chromium-browser google-chrome"
  fi

  if [ -z "$BROWSER" ]; then
    for executable in $BROWSERS; do
      BROWSER=`which $executable 2> /dev/null`
      if [ -n "$BROWSER" ]; then
        break;
      fi
    done
  fi

  if [ -z "$BROWSER" ]; then
    (sleep 5; echo -e "We are sorry... We tried all we could do but we couldn't locate your default browser... \nIf you want to see our default website please open your browser and insert this URL:\nhttp://localhost:8080/";) &
  else
    URL="http://localhost:8080/operate/login"
    ( checkCamundaStartup "$URL" "Camunda"; $BROWSER "http://localhost:8080/operate/login"; ) &
  fi

  # start Camunda Run in the background
  exec $runScript start --detached

else
  # start Camunda Run with the passed arguments
  exec $runScript start "$@"
fi

