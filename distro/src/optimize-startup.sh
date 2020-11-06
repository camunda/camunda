#!/bin/bash

###############################################################################
#                                                                             #
#                     Optimize Production Startup Script                      #
#                                                                             #
###############################################################################
#
# Note: This script is supposed to be used in production
# to start-up Optimize. For further information please consult
# the documentation: https://docs.camunda.org/optimize/${docs.version}/technical-guide/setup/installation/
#
# Optionally, you can overwrite the default JVM options by setting the `OPTIMIZE_JAVA_OPTS`
# variable.

cd $(dirname "$0")
BASEDIR=$(pwd)

# now set the path to java
if [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  set +e
  JAVA=$(command -v java)
  set -e
fi

# check if there are custom JVM options set.
if [ -z "$OPTIMIZE_JAVA_OPTS" ]; then
  OPTIMIZE_JAVA_OPTS="-Xms1024m -Xmx1024m -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=256m"
fi

# check if debug mode should be enabled
if [ "$1" == "--debug" ]; then
  DEBUG_PORT=9999
  DEBUG_JAVA_OPTS="-Xdebug -agentlib:jdwp=transport=dt_socket,address=$DEBUG_PORT,server=y,suspend=n"
fi

# Set up the optimize classpaths, i.e. add the config folder, the Optimize back-end dependencies
# and the optimize jar
OPTIMIZE_CLASSPATH="${BASEDIR}/config:${BASEDIR}/lib/*:${BASEDIR}/optimize-backend-${project.version}.jar"

RUN_UPGRADE=false
for argument in "$@"
do
    if [[ "$argument" =~ ^-D.* ]]; then
      # forward any set java properties
      JAVA_SYSTEM_PROPERTIES="$JAVA_SYSTEM_PROPERTIES $argument"
    elif [[ "$argument" =~ ^--upgrade$ ]]; then
      RUN_UPGRADE=true
    fi
done

if [ $RUN_UPGRADE == true ]; then
  bash ${BASEDIR}/upgrade/upgrade.sh --skip-warning
fi

echo
echo "Starting Camunda Optimize ${project.version}..."
echo

exec $JAVA ${OPTIMIZE_JAVA_OPTS} -cp ${OPTIMIZE_CLASSPATH} ${DEBUG_JAVA_OPTS} ${JAVA_SYSTEM_PROPERTIES} -Dfile.encoding=UTF-8 org.camunda.optimize.Main
