#!/bin/bash -xeu
mvn -B -s "${MAVEN_SETTINGS_XML}" -T1C -DskipTests clean install -Pprepare-offline,-PcheckFormat,-autoFormat
