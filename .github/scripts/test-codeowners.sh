#!/usr/bin/env bash
# Tests for .codeowners ownership rules using codeowners-plus CLI.
# Run from the repo root: .github/scripts/test-codeowners.sh
set -euo pipefail

WORKTREE_ROOT="$(git rev-parse --show-toplevel)"
# codeowners-cli requires a real .git directory; git worktrees have a .git file.
# Fall back to the common git dir's parent (the main checkout) when in a worktree.
if [[ -f "${WORKTREE_ROOT}/.git" ]]; then
  REPO_ROOT="$(dirname "$(git rev-parse --git-common-dir)")"
else
  REPO_ROOT="${WORKTREE_ROOT}"
fi
CODEOWNERS_CLI="${CODEOWNERS_CLI:-codeowners-cli}"
PASS=0
FAIL=0
BUGS=0

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# assert_owner <description> <file> <expected_owners...>
# Checks that all expected owners appear in the 'required' list.
assert_owner() {
  local description="$1"
  local file="$2"
  shift 2
  local expected=("$@")

  local json stderr_out exit_code=0
  json=$("${CODEOWNERS_CLI}" owner --root "${REPO_ROOT}" --format json "${file}" 2>/tmp/_co_stderr) || exit_code=$?
  stderr_out=$(cat /tmp/_co_stderr 2>/dev/null || true)
  if [[ $exit_code -ne 0 ]] || echo "${stderr_out}" | grep -q "^Error:"; then
    echo -e "  ${RED}ERROR${NC}: ${description} (${file}): CLI error: ${stderr_out}"
    (( FAIL++ )) || true
    return
  fi

  local actual_required
  actual_required=$(echo "${json}" | jq -r --arg f "${file}" '.[$f].required[]?' | sort | tr '\n' ' ' | sed 's/ $//')

  local missing=()
  for owner in "${expected[@]}"; do
    if ! echo "${actual_required}" | grep -qF "${owner}"; then
      missing+=("${owner}")
    fi
  done

  if [[ ${#missing[@]} -eq 0 ]]; then
    echo -e "  ${GREEN}PASS${NC}: ${description}"
    (( PASS++ )) || true
  else
    echo -e "  ${RED}FAIL${NC}: ${description}"
    echo -e "        file:     ${file}"
    echo -e "        expected: ${expected[*]}"
    echo -e "        actual:   ${actual_required:-"(none)"}"
    echo -e "        missing:  ${missing[*]}"
    (( FAIL++ )) || true
  fi
}

# assert_owner_bug: same as assert_owner but marks it as a known bug.
# Test is expected to FAIL — it documents broken behavior.
assert_owner_bug() {
  local description="$1"
  local file="$2"
  shift 2
  local expected=("$@")

  local json stderr_out exit_code=0
  json=$("${CODEOWNERS_CLI}" owner --root "${REPO_ROOT}" --format json "${file}" 2>/tmp/_co_stderr) || exit_code=$?
  stderr_out=$(cat /tmp/_co_stderr 2>/dev/null || true)
  if [[ $exit_code -ne 0 ]] || echo "${stderr_out}" | grep -q "^Error:"; then
    echo -e "  ${RED}ERROR${NC} [BUG]: ${description}: CLI error"
    (( BUGS++ )) || true
    return
  fi

  local actual_required
  actual_required=$(echo "${json}" | jq -r --arg f "${file}" '.[$f].required[]?' | sort | tr '\n' ' ' | sed 's/ $//')

  local missing=()
  for owner in "${expected[@]}"; do
    if ! echo "${actual_required}" | grep -qF "${owner}"; then
      missing+=("${owner}")
    fi
  done

  if [[ ${#missing[@]} -gt 0 ]]; then
    echo -e "  ${YELLOW}BUG CONFIRMED${NC}: ${description}"
    echo -e "        file:     ${file}"
    echo -e "        expected: ${expected[*]}"
    echo -e "        actual:   ${actual_required:-"(none)"}"
    echo -e "        missing (shadowed): ${missing[*]}"
    (( BUGS++ )) || true
  else
    echo -e "  ${GREEN}BUG FIXED${NC}: ${description} — expected owners now present"
    (( PASS++ )) || true
  fi
}

echo ""
echo "========================================"
echo " .codeowners ownership tests"
echo "========================================"

# ── /qa/ default ownership ────────────────────────────────────────────────────
echo ""
echo "── /qa/ default (qa-engineering) ──"

assert_owner \
  "qa/pom.xml → qa-engineering" \
  "qa/pom.xml" \
  "@camunda/qa-engineering"

# ── /qa/ overrides: orchestration-cluster ────────────────────────────────────
echo ""
echo "── /qa/ overrides → orchestration-cluster ──"

assert_owner \
  "qa/archunit-tests/ → orchestration-cluster" \
  "qa/archunit-tests/src/test/java/io/camunda/ForbidSpringProblemDetailArchTest.java" \
  "@camunda/orchestration-cluster"

assert_owner \
  "qa/util/ → orchestration-cluster" \
  "qa/util/src/test/java/io/camunda/qa/util/multidb/MultiDbConfiguratorTest.java" \
  "@camunda/orchestration-cluster"

# ── /qa/acceptance-tests dual ownership: util/ ───────────────────────────────
echo ""
echo "── /qa/acceptance-tests/util/ → dual ownership ──"

assert_owner \
  "acceptance-tests util/ → qa-engineering AND orchestration-cluster" \
  "qa/acceptance-tests/src/test/java/io/camunda/it/util/TestHelper.java" \
  "@camunda/qa-engineering" "@camunda/orchestration-cluster"

# ── /qa/acceptance-tests → camundaex ─────────────────────────────────────────
echo ""
echo "── /qa/acceptance-tests → camundaex ──"

assert_owner \
  "acceptance-tests client/ → camundaex" \
  "qa/acceptance-tests/src/test/java/io/camunda/it/client/UnassignClientFromTenantIT.java" \
  "@camunda/camundaex"

assert_owner \
  "acceptance-tests spring/ → camundaex" \
  "qa/acceptance-tests/src/test/java/io/camunda/it/spring/AbstractSpringDependenciesTest.java" \
  "@camunda/camundaex"

# ── /qa/acceptance-tests → identity ──────────────────────────────────────────
echo ""
echo "── /qa/acceptance-tests → identity ──"

assert_owner \
  "acceptance-tests identity/ → identity" \
  "qa/acceptance-tests/src/test/java/io/camunda/it/identity/InitializeInvalidTenantIT.java" \
  "@camunda/identity"

assert_owner \
  "acceptance-tests auth/ → identity" \
  "qa/acceptance-tests/src/test/java/io/camunda/it/auth/UsageMetricAuthorizationIT.java" \
  "@camunda/identity"

assert_owner \
  "acceptance-tests oidc/ → identity" \
  "qa/acceptance-tests/src/test/java/io/camunda/it/oidc/OverlappingUsernameAndClientIdClaimTest.java" \
  "@camunda/identity"

assert_owner \
  "acceptance-tests csrf/ → identity" \
  "qa/acceptance-tests/src/test/java/io/camunda/it/csrf/CsrfTokenIT.java" \
  "@camunda/identity"

assert_owner \
  "acceptance-tests logout/ → identity" \
  "qa/acceptance-tests/src/test/java/io/camunda/it/logout/BasicAuthLogoutIT.java" \
  "@camunda/identity"

# ── /qa/acceptance-tests → zeebe-distributed-platform ───────────────────────
echo ""
echo "── /qa/acceptance-tests → zeebe-distributed-platform ──"

assert_owner \
  "acceptance-tests cluster/ → zeebe-distributed-platform" \
  "qa/acceptance-tests/src/test/java/io/camunda/it/cluster/ClusterPurgeMultiDbIT.java" \
  "@camunda/zeebe-distributed-platform"

assert_owner \
  "acceptance-tests backup/ → zeebe-distributed-platform" \
  "qa/acceptance-tests/src/test/java/io/camunda/it/backup/AbstractBackupRestoreIT.java" \
  "@camunda/zeebe-distributed-platform"

assert_owner \
  "acceptance-tests network/ → zeebe-distributed-platform" \
  "qa/acceptance-tests/src/test/java/io/camunda/it/network/SecuredClusterMessagingIT.java" \
  "@camunda/zeebe-distributed-platform"

# ── /qa/acceptance-tests → data-layer ────────────────────────────────────────
echo ""
echo "── /qa/acceptance-tests → data-layer ──"

assert_owner \
  "acceptance-tests rdbms/db/ → data-layer (direct file)" \
  "qa/acceptance-tests/src/test/java/io/camunda/it/rdbms/db/RdbmsFlushRollbackIT.java" \
  "@camunda/data-layer"

assert_owner \
  "acceptance-tests rdbms/db/subdir/ → data-layer (nested file)" \
  "qa/acceptance-tests/src/test/java/io/camunda/it/rdbms/db/agentinstance/AgentInstanceSortIT.java" \
  "@camunda/data-layer"

assert_owner \
  "acceptance-tests schema/ → data-layer" \
  "qa/acceptance-tests/src/test/java/io/camunda/it/schema/ExporterMigrationTestHelper.java" \
  "@camunda/data-layer"

assert_owner \
  "acceptance-tests nodb/ → data-layer" \
  "qa/acceptance-tests/src/test/java/io/camunda/it/nodb/BasicAuthNoSecondaryStorageTest.java" \
  "@camunda/data-layer"

assert_owner \
  "acceptance-tests historycleanup/ → data-layer" \
  "qa/acceptance-tests/src/test/java/io/camunda/it/historycleanup/HistoryCleanupIT.java" \
  "@camunda/data-layer"

# ── /qa/acceptance-tests → core-features ─────────────────────────────────────
echo ""
echo "── /qa/acceptance-tests → core-features ──"

assert_owner \
  "acceptance-tests task/ → core-features" \
  "qa/acceptance-tests/src/test/java/io/camunda/it/task/UserTaskIT.java" \
  "@camunda/core-features"

assert_owner \
  "acceptance-tests orchestration/ → core-features" \
  "qa/acceptance-tests/src/test/java/io/camunda/it/orchestration/JobIT.java" \
  "@camunda/core-features"

assert_owner \
  "acceptance-tests mcp/ → core-features" \
  "qa/acceptance-tests/src/test/java/io/camunda/it/mcp/McpServerConfigurationIT.java" \
  "@camunda/core-features"

assert_owner \
  "acceptance-tests tenancy/ → core-features" \
  "qa/acceptance-tests/src/test/java/io/camunda/it/tenancy/DecisionDefinitionTenancyIT.java" \
  "@camunda/core-features"

assert_owner \
  "acceptance-tests auditlog/ → core-features" \
  "qa/acceptance-tests/src/test/java/io/camunda/it/auditlog/AuditLogWithoutAuthorizationsIT.java" \
  "@camunda/core-features"

assert_owner \
  "acceptance-tests historydeletion/ → core-features" \
  "qa/acceptance-tests/src/test/java/io/camunda/it/historydeletion/DeleteDecisionInstanceHistoryIT.java" \
  "@camunda/core-features"

assert_owner \
  "acceptance-tests document/ → core-features" \
  "qa/acceptance-tests/src/test/java/io/camunda/it/document/ESClient.java" \
  "@camunda/core-features"

# ── /zeebe/qa/integration-tests ordering bug ─────────────────────────────────
echo ""
echo "── /zeebe/qa/integration-tests ownership ──"
echo "   (cluster/shared/util → zeebe-distributed-platform pending https://github.com/multimediallc/codeowners-plus/issues/154)"

assert_owner_bug \
  "zeebe IT cluster/ → zeebe-distributed-platform (pending codeowners-plus sort fix)" \
  "zeebe/qa/integration-tests/src/test/java/io/camunda/zeebe/it/cluster/config/IdleStrategyConfigIT.java" \
  "@camunda/zeebe-distributed-platform"

assert_owner_bug \
  "zeebe IT shared/ → zeebe-distributed-platform (pending codeowners-plus sort fix)" \
  "zeebe/qa/integration-tests/src/test/java/io/camunda/zeebe/it/shared/smoke/NoSecondaryStorageSmokeIT.java" \
  "@camunda/zeebe-distributed-platform"

assert_owner_bug \
  "zeebe IT util/ → zeebe-distributed-platform (pending codeowners-plus sort fix)" \
  "zeebe/qa/integration-tests/src/test/java/io/camunda/zeebe/it/util/ZeebeContainerUtil.java" \
  "@camunda/zeebe-distributed-platform"

assert_owner \
  "zeebe IT engine/ → core-features" \
  "zeebe/qa/integration-tests/src/test/java/io/camunda/zeebe/it/engine/processing/GlobalListenersInitializerIT.java" \
  "@camunda/core-features"

assert_owner \
  "zeebe IT exporter/ → core-features" \
  "zeebe/qa/integration-tests/src/test/java/io/camunda/zeebe/it/exporter/AnalyticsExporterIT.java" \
  "@camunda/core-features"

# ── .claude/skills overrides ──────────────────────────────────────────────────
echo ""
echo "── .claude/skills overrides ──"

assert_owner \
  ".claude/skills/engine-expert/ → core-features (overrides orchestration-cluster)" \
  ".claude/skills/engine-expert/SKILL.md" \
  "@camunda/core-features"

assert_owner \
  ".claude/skills/load-test-ops/ → reliability-testing (overrides orchestration-cluster)" \
  ".claude/skills/load-test-ops/SKILL.md" \
  "@camunda/reliability-testing"

assert_owner \
  ".claude/skills/ci-validation/ → monorepo-devops-team (overrides orchestration-cluster)" \
  ".claude/skills/ci-validation/SKILL.md" \
  "@camunda/monorepo-devops-team"

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
echo "========================================"
echo " Results"
echo "========================================"
echo -e "  ${GREEN}PASS${NC}:          ${PASS}"
echo -e "  ${RED}FAIL${NC}:          ${FAIL}"
echo -e "  ${YELLOW}BUGS CONFIRMED${NC}: ${BUGS}"
echo ""

if [[ ${FAIL} -gt 0 ]]; then
  exit 1
fi
exit 0
