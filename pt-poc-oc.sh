#!/usr/bin/env bash
#
# Boots OC under the pt-poc profile (which pulls in consolidated-auth,
# pt-security, and rdbmsH2 via the spring.profiles.group declaration in
# application.properties). The rdbmsH2 profile points secondary storage at an
# in-memory H2 database so no Elasticsearch or external DB is required.
# Stdout/stderr both stream to the terminal AND tee to /tmp/oc.log so the log
# can be inspected after the fact.
#
# Prerequisite: run ./pt-poc-idp.sh in a different terminal first so the Keycloak
# realms are reachable on :8081 and :8082.
#
# Usage:
#   ./pt-poc-oc.sh
#
# Once Tomcat reports "Tomcat started on port 8080", open in a browser:
#   http://localhost:8080/physical-tenant/tenanta/whoami
#
# Press Ctrl-C to stop. The log persists at /tmp/oc.log.
#
set -euo pipefail

cd "$(dirname "$0")"

# spring-boot:run -pl dist resolves authentication/etc. from the local Maven
# repo — it does NOT recompile upstream modules from source. We `install`
# dist + its dependencies (-am) first so any changes in authentication/, etc.
# land in OC before boot.
#
# -DskipChecks skips the spotless/license/code-style checks (we ran them on
# commit; rerunning each boot is noise). -PskipFrontendBuild skips the
# webapp/client npm build which the PoC does not need.
./mvnw install -pl dist -am -Dquickly -DskipChecks -PskipFrontendBuild -T1C

exec ./mvnw -pl dist spring-boot:run \
  -DskipChecks -PskipFrontendBuild \
  -Dspring-boot.run.profiles=pt-poc 2>&1 | tee /tmp/oc.log
