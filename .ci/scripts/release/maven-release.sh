#!/bin/bash

mvn -s .ci/settings.xml release:prepare release:perform -B \
    -Dgpg.passphrase="${GPG_PASS}" \
    -Dresume=false \
    -Dtag=${RELEASE_VERSION} \
    -DreleaseVersion=${RELEASE_VERSION} \
    -DdevelopmentVersion=${DEVELOPMENT_VERSION} \
    -DpushChanges=${PUSH_CHANGES} \
    -DremoteTagging=${PUSH_CHANGES} \
    -DlocalCheckout=${SKIP_DEPLOY} \
    -Darguments='--settings=settings.xml -DskipTests=true -Dskip-zbctl=false -Dgpg.passphrase="${GPG_PASS}" -Dskip.central.release=${SKIP_DEPLOY} -Dskip.camunda.release=${SKIP_DEPLOY}'
