#!/usr/bin/env bash
#
# Boots two Keycloak realms ("default" on :8081, "tenanta" on :8082) for the
# Physical Tenant Spring Security PoC. Leave this running in a dedicated
# terminal; press <enter> in the same terminal to stop.
#
# Requires Docker. First invocation pulls the Keycloak image (~500 MB) so allow
# 30-60s. Subsequent runs start in 5-10s.
#
# Usage:
#   ./pt-poc-idp.sh
#
# Once the banner "=== PT-PoC local IdPs ready ===" prints, the realms are
# reachable at:
#   http://localhost:8081/realms/default
#   http://localhost:8082/realms/tenanta
#
set -euo pipefail

cd "$(dirname "$0")"

exec ./mvnw -pl dist test-compile exec:java \
  -Dexec.mainClass=io.camunda.application.pt.PtPocLocalIdpRunner \
  -Dexec.classpathScope=test
