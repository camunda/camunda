#!/bin/sh -eux

mvn dependency:get -B \
    -DremoteRepositories="camunda-nexus::::https://app.camunda.com/nexus/content/repositories/public" \
    -DgroupId="io.zeebe" -DartifactId="zeebe-distribution" \
    -Dversion="${VERSION}" -Dpackaging="tar.gz" -Dtransitive=false

mvn dependency:copy -B \
    -Dartifact="io.zeebe:zeebe-distribution:${VERSION}:tar.gz" \
    -DoutputDirectory=${WORKSPACE} \
    -Dmdep.stripVersion=true
