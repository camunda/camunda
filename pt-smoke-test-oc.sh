#!/usr/bin/env bash
#
# INTERIM — drop before review. Part of the local PT API smoke harness.
#
# Boots the OC under the pt-smoke-test profile, which expands (via spring.profiles.group.pt-smoke-test
# in application.properties) into: consolidated-auth, elasticsearch, broker.
#
#   - consolidated-auth: activates the host security graph and AuthenticationConfiguration
#   - elasticsearch:     ES secondary storage on localhost:9200 (required for multi-PT
#                        PhysicalTenantSearchClientReadersConfiguration to activate)
#   - broker:            embedded Zeebe broker (needed for /v2/authentication/me to respond)
#
# The per-tenant SecurityFilterChain wiring activates automatically once
# camunda.physical-tenants.* entries are present in application-pt-smoke-test.yaml.
#
# Prerequisite: run ./pt-smoke-test-idp.sh in a separate terminal first.
#
# Usage:
#   ./pt-smoke-test-oc.sh                                  # base config (profile: pt-smoke-test)
#   ./pt-smoke-test-oc.sh pt-smoke-test,pt-smoke-test-default-narrowed   # + scenario A overlay (default narrowed)
#   ./pt-smoke-test-oc.sh pt-smoke-test,pt-smoke-test-oidc-keep          # + scenario C overlay (tenanta keeps oidc)
#
# The optional argument is the Spring profiles list. A variant adds an extra profile whose
# application-<profile>.yaml layers a small override onto application-pt-smoke-test.yaml — pt-smoke-test must
# stay first so its profile group (consolidated-auth, elasticsearch, broker) still expands.
#
# Once Tomcat reports "Tomcat started on port 8080", run the matching ./pt-smoke-test-api*.sh.
# Press Ctrl-C to stop. Logs stream to the terminal and tee to /tmp/oc.log.
#
set -euo pipefail

cd "$(dirname "$0")"

# Truncate the log up front so stale output from a previous session doesn't linger.
rm -f /tmp/oc.log

# Always start the broker from clean state. The persisted topology (dist/data/.topology.meta)
# and RocksDB survive across runs; a leftover/duplicate exporter entry (e.g. a stale
# "camundaexporter" alongside "camundaExporter") then opens with the wrong config and the
# exporter is stuck retrying "Schema is not ready for use". Wiping dist/data rebuilds the
# topology from the current config every run. Local dev data only — safe to delete.
echo "Clearing broker data (dist/data)..."
rm -rf dist/data

# Step 1: build dist + all upstream modules so spring-boot:run picks up fresh classes,
# INCLUDING the webapp frontends (Operate/Tasklist UI). We skip tests and checks for speed but
# force the frontend build with -Dskip.fe.build=false. NOTE: do NOT pass -Dquickly here — the
# parent pom sets `skip.fe.build = ${quickly}`, so -Dquickly would skip the npm build and the UI
# would 404 (e.g. /operate/forbidden). The first build is slow (npm); once the webapp jars are in
# ~/.m2 you can re-comment this line to make subsequent runs fast.
#
# -Dmaven.build.cache.enabled=false is REQUIRED: the build cache can restore a stale dist
# target/classes (e.g. an application.properties without our spring.profiles.group.pt-smoke-test line),
# so the profile group silently fails to expand and consolidated-auth/operate/etc. never activate
# — every endpoint then 404s. Disabling the cache forces resources to be re-copied each run.
# `clean` guarantees the authentication module (and all reactor modules) recompile from source —
# belt-and-braces against any stale per-tenant auth-config classes from an earlier build.
./mvnw clean install -pl dist -am -DskipTests -DskipChecks -Dskip.fe.build=false -Dmaven.build.cache.enabled=false -T1C

# Step 2: boot OC under the pt-smoke-test profile (plus any variant overlay profile passed as $1).
PROFILES="${1:-pt-smoke-test}"
echo "Booting OC with Spring profiles: $PROFILES"
exec ./mvnw -pl dist spring-boot:run \
  -DskipChecks \
  -Dspring-boot.run.profiles="$PROFILES" 2>&1 | tee /tmp/oc.log
