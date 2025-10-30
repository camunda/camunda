#!/bin/bash

# This file will be deleted before merging.

mvn -f dist/pom.xml exec:java -Dexec.mainClass="io.camunda.application.StandaloneCamunda"
