#!/bin/sh -xe

# kill elasticsearch
PID="$(ps aux | grep -v grep | grep elasticsearch | awk '{ print $2 }')"
if [ -z "$PID" ]
    then
    echo "No elasticsearch to kill"
    else
    kill -9 $PID
fi

# kill engine
PID="$(ps aux | grep -v grep | grep tomcat | awk '{ print $2 }')"
if [ -z "$PID" ]
    then
    echo "No engine to kill"
    else
    kill -9 $PID
fi


