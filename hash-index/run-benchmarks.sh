#!/bin/bash

mvn clean package -Pbenchmarks -DskipTests

java -jar target/hash-index-benchmarks.jar -f 2 -i 5 -wi 5 -tu s
