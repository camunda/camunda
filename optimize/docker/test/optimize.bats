#!/usr/bin/env bats

setup() {
  cd "${BATS_TEST_TMPDIR}"
  cp "${BATS_TEST_DIRNAME}/../bin/optimize.sh" .
  cat > optimize-startup.sh <<'EOF'
#!/bin/bash
trap 'echo TERM > signal; exit 143' TERM
echo "$*" > args
echo "$$" > pid
while true; do sleep 0.1; done
EOF
}

teardown() {
  if [ -s "${BATS_TEST_TMPDIR}/pid" ]; then
    kill -KILL "$(cat "${BATS_TEST_TMPDIR}/pid")" 2>/dev/null || true
  fi
}

@test "optimize.sh: SIGTERM reaches optimize-startup.sh, arguments forwarded" {
  ./optimize.sh --upgrade -Dfoo=bar 3>&- &
  local pid=$!
  for _ in $(seq 50); do
    [ -s pid ] && break
    sleep 0.1
  done

  [ "$(cat pid)" = "${pid}" ]
  [ "$(cat args)" = "--upgrade -Dfoo=bar" ]

  kill -TERM "${pid}"
  local status=0
  wait "${pid}" || status=$?

  [ "${status}" -eq 143 ]
  [ "$(cat signal)" = TERM ]
}
