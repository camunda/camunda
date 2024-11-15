#!/bin/bash

BASEDIR=$(dirname "$0")
runScript="$BASEDIR/internal/run.sh"
POLLING_CAMUNDA_PID_PATH="$BASEDIR/camunda-polling.pid"

function printEndpoints {
  echo
  cat endpoints.txt
}

function checkCamundaStartup {
  local RETRIES=24
  local SLEEP_TIME=14
  local URL=$1
  local NAME=$2
  local COMMAND="curl -XGET $URL"

  until $COMMAND &>/dev/null; do
    sleep $SLEEP_TIME
    RETRIES=$((RETRIES - 1))
    if [ $RETRIES -eq 0 ]; then
      echo "Error: $NAME did not start!"
      exit 1
    else
      echo "Waiting for $NAME to start. $RETRIES retries left"
    fi
  done
  echo "$NAME has successfully been started."
  printEndpoints
}

function killPolling {
  if [ -f "$POLLING_CAMUNDA_PID_PATH" ]; then
    local polling_pid
    polling_pid=$(cat "$POLLING_CAMUNDA_PID_PATH")
    kill -9 "$polling_pid"
    rm "$POLLING_CAMUNDA_PID_PATH"
  fi
}

function childExitHandler {
  local status=$2
  if [ "$status" -eq 0 ]; then
    return
  fi

  if [ "$status" -eq 1 ]; then
    killPolling
  fi
}

trap killPolling SIGINT ERR SIGTERM
trap 'childExitHandler $? $?' SIGCHLD

if [ $# -eq 0 ]; then
  # open a browser (must be done first)
  UNAME=$(which uname)
  if [ -n "$UNAME" ] && [ "$($UNAME)" = "Darwin" ]; then
    BROWSERS="open"
  else
    BROWSERS="xdg-open gnome-www-browser x-www-browser firefox chromium chromium-browser google-chrome"
  fi

  if [ -z "$BROWSER" ]; then
    for executable in $BROWSERS; do
      BROWSER=$(which "$executable" 2>/dev/null)
      if [ -n "$BROWSER" ]; then
        break
      fi
    done
  fi

  if [ -z "$BROWSER" ]; then
    (sleep 5; echo -e "We are sorry... We tried all we could do but we couldn't locate your default browser... \nIf you want to see our default website please open your browser and insert this URL:\nhttp://localhost:8080/";) &
  else
    URL="http://localhost:8080/operate/login"
    (checkCamundaStartup "$URL" "Camunda"; $BROWSER "$URL";) &
    echo $! > "$POLLING_CAMUNDA_PID_PATH"
  fi

  # start Camunda Run in the foreground
  exec "$runScript" start
else
  # start Camunda Run with the passed arguments
  exec "$runScript" start "$@"
fi
