#!/usr/bin/env bash
#
# Boots OC under the pt-poc profile (which pulls in consolidated-auth and
# pt-security via the spring.profiles.group declaration in application.properties).
# Stdout/stderr both stream to the terminal AND tee to /tmp/oc.log so the log can
# be inspected after the fact.
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

exec ./mvnw -pl dist spring-boot:run \
  -Dspring-boot.run.profiles=pt-poc 2>&1 | tee /tmp/oc.log
