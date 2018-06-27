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
#
# Optionally, you can overwrite the default JVM options by setting the `OPTIMIZE_JAVA_OPTS`
# variable.

BASEDIR=$(dirname "$0")

echo
echo "Starting Camunda Optimize...";
echo

# now set the path to java
if [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  set +e
  JAVA=`which java`
  set -e
fi

# check if there are custom JVM options set.
if [ -z "$OPTIMIZE_JAVA_OPTS" ]; then
  OPTIMIZE_JAVA_OPTS="-Xms1024m -Xmx1024m -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=256m"
fi

# Set up the optimize classpaths, i.e. add the environment folder, all jars in the
# plugin directory and the optimize jar
OPTIMIZE_CLASSPATH=$BASEDIR/environment:$BASEDIR/plugin/*:$BASEDIR/optimize-backend-${project.version}.jar

exec $JAVA ${OPTIMIZE_JAVA_OPTS} -cp $OPTIMIZE_CLASSPATH -Dfile.encoding=UTF-8 org.camunda.optimize.Main
