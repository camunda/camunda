#!/bin/bash

mvn clean package -Pbenchmarks -DskipTests

java -jar target/zb-logstreams-benchmarks.jar -f 2 -i 5 -wi 3 -tu s

