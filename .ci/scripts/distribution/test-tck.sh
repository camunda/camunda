#!/bin/bash -xeu

git clone https://github.com/zeebe-io/bpmn-tck.git

cd bpmn-tck
# ./bpmn-tck/

mvn -B test -s ${MAVEN_SETTINGS_XML} -DzeebeImage=zeebe-hazelcast-exporter -DzeebeImageVersion=current-test

cd ..
# ./
