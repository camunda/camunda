#!/bin/sh -e

PID="$(ps aux | grep -v grep | grep elasticsearch | awk '{ print $2 }')"
if [ -z "$PID" ]
    then
    echo "Nothing to kill"
    else
    kill -9 $PID
fi