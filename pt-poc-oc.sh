#!/usr/bin/env bash
#
# Boots OC under the pt-poc profile (which pulls in consolidated-auth and
# rdbmsH2 via the spring.profiles.group declaration in application.properties).
# The rdbmsH2 profile points secondary storage at an in-memory H2 database so
# no Elasticsearch or external DB is required. The per-tenant SecurityFilterChain
# wiring activates from the camunda.physical-tenants.* entries in
# application-pt-poc.yaml — no separate profile required.
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

# Truncate the log up front. `tee` without -a also truncates on its first
# write, but logback's file appender (logging.file.name in application-pt-poc
# .yaml) opens in append mode — between them, a stale log from a previous
# session can linger across restarts. Explicit rm makes it deterministic.
rm -f /tmp/oc.log

# spring-boot:run -pl dist resolves authentication/etc. from the local Maven
# repo — it does NOT recompile upstream modules from source. Two steps:
#  1. install -pl dist -am: rebuilds dist + every upstream module
#     (authentication, security, …) so OC boots against fresh classes.
#  2. spring-boot:run -pl dist (no -am): starts OC. We cannot chain these
#     in one Maven invocation because spring-boot:run would be applied to
#     every module in the -am reactor, and upstream modules don't have the
#     plugin configured (No plugin found for prefix 'spring-boot').
#
# -DskipChecks skips spotless/license/code-style validation (we ran it on
# commit; rerunning each boot is noise). -PskipFrontendBuild skips the
# webapp/client npm build which the PoC does not need.
./mvnw install -pl dist -am -Dquickly -DskipChecks -PskipFrontendBuild -T1C

exec ./mvnw -pl dist spring-boot:run \
  -DskipChecks -PskipFrontendBuild \
  -Dspring-boot.run.profiles=pt-poc 2>&1 | tee /tmp/oc.log
