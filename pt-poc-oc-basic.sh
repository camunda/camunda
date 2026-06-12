#!/usr/bin/env bash
#
# INTERIM — drop before review. Part of the local PT smoke harness (BASIC-auth variant).
#
# Boots the OC under the pt-poc-basic profile, which expands (via spring.profiles.group.pt-poc-basic
# in application.properties) into the same members as the OIDC harness — consolidated-auth, broker,
# identity, tasklist, operate — but activates application-pt-poc-basic.yaml (method=basic, users
# seeded per tenant via camunda.security.initialization). No external IdP is needed for basic auth.
#
# Prerequisite: Elasticsearch on localhost:9200 (users are stored in secondary storage). No
# ./pt-poc-idp.sh needed.
#
# Usage:
#   ./pt-poc-oc-basic.sh
#
# Once Tomcat reports "Tomcat started on port 8080", wait a few seconds for the broker exporter to
# seed the initialization users into ES, then run ./pt-poc-basic-smoke.sh. Logs stream to the
# terminal and tee to /tmp/oc-basic.log. Press Ctrl-C to stop.
#
set -euo pipefail

cd "$(dirname "$0")"

# Truncate the log up front so stale output from a previous session doesn't linger.
rm -f /tmp/oc-basic.log

# Always start the broker from clean state (see pt-poc-oc.sh for the rationale): a leftover/stale
# exporter entry otherwise opens with the wrong config and gets stuck on "Schema is not ready".
echo "Clearing broker data (dist/data)..."
rm -rf dist/data

# Step 1: build dist + all upstream modules. See pt-poc-oc.sh for why -Dmaven.build.cache.enabled
# =false and `clean` are required (avoid stale resources/classes) and why -Dskip.fe.build=false is
# needed (webapp frontends). The first build is slow (npm); subsequent runs reuse the cached jars.
./mvnw clean install -pl dist -am -DskipTests -DskipChecks -Dskip.fe.build=false -Dmaven.build.cache.enabled=false -T1C

# Step 2: boot OC under the pt-poc-basic profile.
exec ./mvnw -pl dist spring-boot:run \
  -DskipChecks \
  -Dspring-boot.run.profiles=pt-poc-basic 2>&1 | tee /tmp/oc-basic.log
