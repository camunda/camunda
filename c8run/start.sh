#!/bin/bash

BASEDIR=$(dirname "$0")
runScript=$BASEDIR/internal/run.sh

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
    (sleep 120; $BROWSER "http://localhost:8080/operate/login";) &
  fi

  # start Camunda Run in the background
  exec $runScript start --detached

else
  # start Camunda Run with the passed arguments
  exec $runScript start "$@"
fi

