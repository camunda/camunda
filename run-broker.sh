#!/bin/bash

# clear terminal buffer
clear && printf '\e[3J'

CAMUNDA_SECURITY_INITIALIZATION_USERS_0_NAME=Demo \
CAMUNDA_SECURITY_INITIALIZATION_USERS_0_USERNAME=demo \
CAMUNDA_SECURITY_INITIALIZATION_USERS_0_EMAIL=demo@example.com \
CAMUNDA_SECURITY_INITIALIZATION_USERS_0_PASSWORD=demo \
CAMUNDA_SECURITY_INITIALIZATION_DEFAULTROLES_ADMIN_USERS_0_=demo \
mvn -f dist/pom.xml exec:java -Dexec.mainClass="io.camunda.application.StandaloneCamunda" \
-Dcamunda.mode=broker \
-Dcamunda.development=true