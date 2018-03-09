#!/bin/sh -xe

mkdir -p ./backend/target/performance-engine-7.8
cd ./backend/target/performance-engine-7.8
wget https://camunda.org/release/camunda-bpm/tomcat/7.8/camunda-bpm-tomcat-7.8.0.tar.gz
tar -xzvf camunda-bpm-tomcat-7.8.0.tar.gz
cp ../../../qa/import-performance-tests/src/test/resources/tomcat/* server/apache-tomcat-8.0.47/conf/
cd server/apache-tomcat-8.0.47/lib
wget http://central.maven.org/maven2/org/postgresql/postgresql/42.2.1/postgresql-42.2.1.jar
cd ../../..
./server/apache-tomcat-8.0.47/bin/startup.sh