#!/bin/sh -xe

mkdir -p ./backend/target/performance-engine-7.9
cd ./backend/target/performance-engine-7.9
wget https://camunda.org/release/camunda-bpm/tomcat/7.9/camunda-bpm-tomcat-7.9.0.tar.gz
tar -xzvf camunda-bpm-tomcat-7.9.0.tar.gz
cp ../../../qa/import-performance-tests/src/test/resources/tomcat/* server/apache-tomcat-9.0.5/conf/
cd server/apache-tomcat-9.0.5/lib
wget http://central.maven.org/maven2/org/postgresql/postgresql/42.2.1/postgresql-42.2.1.jar
cd ../../..
./server/apache-tomcat-9.0.5/bin/startup.sh