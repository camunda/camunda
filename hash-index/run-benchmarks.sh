#!/bin/bash

mvn clean package -Pbenchmarks -DskipTests

java -jar target/zb-hash-index-benchmarks.jar -f 2 -i 5 -wi 5 -tu s 

# java -agentpath:/home/meyerd/Programs/honest-profiler/liblagent.so=interval=2,logPath=/tmp/log.hpl -Dagrona.disable.bounds.checks=true -jar target/zb-hash-index-benchmarks.jar -f 2 -i 5 -wi 5 -tu s 
