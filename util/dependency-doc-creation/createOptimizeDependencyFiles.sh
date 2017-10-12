#!/usr/bin/env bash

BASEDIR=$(pwd)
cd ../../backend
## create back end third party
mvn license:download-licenses
## store directory of license file
GET_BACKEND_DIR=`pwd`
LICENSE_DIR=`find ${GET_BACKEND_DIR} -iname "licenses.xml"`

cd ${BASEDIR}
# create the back-end dependencies md file
mvn clean install
mvn exec:java -Dexec.mainClass="org.camunda.optimize.MarkDownDependencyCreator" -Dexec.args=${LICENSE_DIR}
# create front-end dependencies md file
cd ../../client
yarn
yarn run dependencies
cp frontend-dependencies.md ../util/dependency-doc-creation/

