#!/bin/bash
#
# Failure-only diagnostics for release:perform's intermittent `git ls-remote`
# failures. Records whether the remote rejected us (HTTP status) or the runner
# never reached it (DNS/connect). Best-effort: a release is never failed by its
# own diagnostics, so this script always exits 0.
#
# Required env: APP_TOKEN, PROBE_LOG, REF_ADVERTISEMENT
# Run from the repository root (reads release.properties).

set -o nounset
IFS=$'\n\t'

# git and curl both echo URLs back on error, userinfo included.
redact() { sed -E -e 's#//[^@/]*@#//***@#g' -e 's#(authorization|AUTHORIZATION): .*#\1: ***#gI'; }

{
  echo "=== egress IP (correlate with Cloud NAT) ==="
  # api.ipify.org downtime must not affect the remaining probes.
  curl -sS --max-time 10 https://api.ipify.org || echo "ipify unavailable (curl exit=$?)"
  echo
  echo "=== DNS ==="
  getent hosts github.com || echo "getent exit=$?"

  # release:perform clones from the scm.url that release:prepare recorded in
  # release.properties, not from the pom. If its
  # ${env.GITHUB_TOKEN_USR}/${env.GITHUB_TOKEN_PSW} placeholders survived
  # verbatim, the URL carries no usable credentials. Report the verdict, never
  # the value: redact() would scrub it anyway.
  echo "=== scm.url in release.properties ==="
  scm_url=$(sed -n 's/^scm\.url=//p' release.properties 2>/dev/null)
  # SC2016: the unexpanded '${env.' literal is exactly what we are matching on.
  # shellcheck disable=SC2016
  case "$scm_url" in
    '') echo "release.properties missing or without scm.url" ;;
    *'${env.'*) echo "UNRESOLVED placeholders: the URL carries no usable credentials" ;;
    *) echo "placeholders resolved (value withheld)" ;;
  esac

  echo "=== ls-remote from \$TMPDIR, no credentials (anonymous read) ==="
  ( cd "${TMPDIR:-/tmp}" && git ls-remote https://github.com/camunda/camunda.git >/dev/null 2>&1; echo "exit=$?" )
  echo "=== ls-remote from \$TMPDIR, App token (the identity the rest of the job uses) ==="
  ( cd "${TMPDIR:-/tmp}" && git ls-remote "https://x-access-token:${APP_TOKEN}@github.com/camunda/camunda.git" >/dev/null 2>&1; echo "exit=$?" )
  echo "=== ls-remote from the work tree, to isolate the working directory ==="
  git ls-remote https://github.com/camunda/camunda.git >/dev/null 2>&1; echo "exit=$?"

  echo "=== HTTP status of the ref advertisement ls-remote requests ==="
  for auth in anonymous token; do
    if [ "$auth" = anonymous ]; then set -- ; else set -- -u "x-access-token:${APP_TOKEN}"; fi
    printf '%s: ' "$auth"
    curl -sS -o /dev/null --max-time 20 "$@" \
      -w 'http=%{http_code} dns=%{time_namelookup}s connect=%{time_connect}s total=%{time_total}s\n' \
      "${REF_ADVERTISEMENT}" || echo "curl exit=$?"
  done
} 2>&1 | redact | tee "${PROBE_LOG}"

exit 0
