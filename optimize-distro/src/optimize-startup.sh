#!/bin/bash

###############################################################################
#                                                                             #
#                     Optimize Production Startup Script                      #
#                                                                             #
###############################################################################
#
# Note: This script is supposed to be used in production
# to start-up Optimize. For further information please consult
# the documentation: https://docs.camunda.io/optimize/${docs.version}/self-managed/optimize-deployment/install-and-start/
#
# Optionally, you can overwrite the default JVM options by setting the `OPTIMIZE_JAVA_OPTS`
# variable.

echo "[OPTIMIZE-STARTUP] Entering script, current directory: $(pwd)"
cd $(dirname "$0")
BASEDIR=$(pwd)
echo "[OPTIMIZE-STARTUP] BASEDIR set to $BASEDIR"

# now set the path to java
echo "[OPTIMIZE-STARTUP] Checking JAVA_HOME and java executable..."
if [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA="$JAVA_HOME/bin/java"
else
  set +e
  JAVA=$(command -v java)
  set -e
fi
echo "[OPTIMIZE-STARTUP] JAVA resolved to $JAVA"

# check if there are custom JVM options set.
echo "[OPTIMIZE-STARTUP] Checking JVM options..."
if [ -z "$OPTIMIZE_JAVA_OPTS" ]; then
  OPTIMIZE_JAVA_OPTS="-Xms1024m -Xmx1024m -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=256m"
fi
echo "[OPTIMIZE-STARTUP] OPTIMIZE_JAVA_OPTS: $OPTIMIZE_JAVA_OPTS"

# check if debug mode should be enabled
echo "[OPTIMIZE-STARTUP] Checking debug mode..."
if [ "$1" == "--debug" ]; then
  DEBUG_PORT=9999
  DEBUG_JAVA_OPTS="-Xdebug -agentlib:jdwp=transport=dt_socket,address=$DEBUG_PORT,server=y,suspend=n"
  echo "[OPTIMIZE-STARTUP] Debug mode enabled on port $DEBUG_PORT"
fi

# Set up the optimize classpaths, i.e. add the config folder, the Optimize back-end dependencies
# and the optimize jar
echo "[OPTIMIZE-STARTUP] Building classpath..."
OPTIMIZE_CLASSPATH="${BASEDIR}/config:${BASEDIR}/lib/*:${BASEDIR}/optimize-backend-${project.version}.jar"
echo "[OPTIMIZE-STARTUP] OPTIMIZE_CLASSPATH: $OPTIMIZE_CLASSPATH"

echo "[OPTIMIZE-STARTUP] Parsing arguments..."
JAVA_SYSTEM_PROPERTIES=""
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
echo "[OPTIMIZE-STARTUP] JAVA_SYSTEM_PROPERTIES: $JAVA_SYSTEM_PROPERTIES"
echo "[OPTIMIZE-STARTUP] RUN_UPGRADE: $RUN_UPGRADE"

if [ $RUN_UPGRADE == true ]; then
  echo "[OPTIMIZE-STARTUP] Running upgrade script..."
  bash ${BASEDIR}/upgrade/upgrade.sh --skip-warning
fi

echo
echo "Starting Camunda Optimize ${project.version}..."
echo

exec $JAVA ${OPTIMIZE_JAVA_OPTS} -cp "${OPTIMIZE_CLASSPATH}" ${DEBUG_JAVA_OPTS} ${JAVA_SYSTEM_PROPERTIES} -Dfile.encoding=UTF-8 io.camunda.optimize.Main
