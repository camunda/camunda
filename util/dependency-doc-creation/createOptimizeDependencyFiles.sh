#!/usr/bin/env bash

BASEDIR=$(pwd)
cd ../../
OPTIMIZE_ROOT=`pwd`
SETTINGS_PATH=`find ${OPTIMIZE_ROOT} -iname "settings.xml"`

cd ./backend

## create back end third party
mvn org.codehaus.mojo:license-maven-plugin:1.2:download-licenses -Dlicense.excludedScopes=test,system,provided -s ${SETTINGS_PATH} -B --fail-at-end

## store directory of license file
GET_BACKEND_DIR=`pwd`
LICENSE_DIR=`find ${GET_BACKEND_DIR} -iname "licenses.xml"`

cd ${BASEDIR}

# create the back-end dependencies md file
mvn clean install -DskipTests -Dskip.docker -s ${SETTINGS_PATH} -B --fail-at-end
mvn exec:java -Dexec.mainClass="org.camunda.optimize.MarkDownDependencyCreator" -Dexec.args=${LICENSE_DIR} -s ${SETTINGS_PATH} -B --fail-at-end

# create front-end dependencies md file
cd ../../client
mvn clean install -DskipTests -Dskip.docker -s ${SETTINGS_PATH} -B --fail-at-end

export PATH=$(pwd)/.node/node/:$PATH
./.node/node/yarn/dist/bin/yarn
./.node/node/yarn/dist/bin/yarn run dependencies
cp frontend-dependencies.md ../util/dependency-doc-creation/
cd ${BASEDIR}

