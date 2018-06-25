#!/bin/bash

###############################################################################
#                                                                             #
#                     Optimize Production Startup Script                      #
#                                                                             #
###############################################################################
#
# Note: This script is supposed to be used in production
# to start-up Optimize. For further information please consult
# the documentation: https://docs.camunda.org/optimize/latest/technical-guide/installation/

BASEDIR=$(dirname "$0")

mkdir -p $BASEDIR/log
mkdir -p $BASEDIR/run

LOG_FILE=$BASEDIR/log/optimize.log
PID_FILE=$BASEDIR/run/optimize.pid

# make sure to kill background/ child processes as well
trap "trap - SIGTERM && kill -- -$$" SIGINT SIGTERM EXIT

echo
echo "Starting Camunda Optimize...";

# Set up the optimize classpaths, i.e. add the environment folder, all jars in the
# plugin directory and the optimize jar
OPTIMIZE_CLASSPATH=$BASEDIR/environment:$BASEDIR/plugin/*:$BASEDIR/optimize-backend-${project.version}.jar

java -cp $OPTIMIZE_CLASSPATH -Dpidfile=$PID_FILE -Dfile.encoding=UTF-8 org.camunda.optimize.Main </dev/null > $LOG_FILE 2>&1 &

echo
echo "Optimize has successfully been started.";
echo "Press CTRL + C to stop optimize!";
echo

wait