#!/usr/bin/env bash
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
# one or more contributor license agreements. See the NOTICE file distributed
# with this work for additional information regarding copyright ownership.
# Camunda licenses this file to you under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with the License.
# You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
# CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
# language governing permissions and limitations under the License.

# Local runner for the C8 Orchestration Cluster triage agent.
#
# Downloads the latest nightly JSON report artifacts from GitHub, runs the
# analysis scripts, and prints the triage report.  Optionally posts to Slack.
#
# Prerequisites:
#   - gh CLI authenticated (gh auth login) or GH_TOKEN env var set
#   - python3
#   - jq
#
# Usage:
#   cd <repo-root>
#   .ci/scripts/ci/triage-tests/run-triage-locally.sh [--slack] [--dry-run]
#
#   --slack      Post the triage summary to #c8-orchestration-cluster-e2e-test-results
#                (requires SLACK_BOT_USER_OAUTH_TOKEN to be set)
#   --dry-run    Analyse only — no Slack post, no fix PR
#
# Examples:
#   # Analyse and print to terminal only
#   GH_TOKEN=ghp_xxx .ci/scripts/ci/triage-tests/run-triage-locally.sh
#
#   # Analyse + post to Slack
#   GH_TOKEN=ghp_xxx SLACK_BOT_USER_OAUTH_TOKEN=xoxb-xxx \
#     .ci/scripts/ci/triage-tests/run-triage-locally.sh --slack

set -euo pipefail

REPO="camunda/camunda"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
WORK_DIR="$(mktemp -d)"
ARTIFACTS_DIR="${WORK_DIR}/artifacts"
POST_TO_SLACK=false
DRY_RUN=false

trap 'rm -rf "${WORK_DIR}"' EXIT

# ── Argument parsing ────────────────────────────────────────────────────────
for arg in "$@"; do
  case "$arg" in
    --slack)   POST_TO_SLACK=true ;;
    --dry-run) DRY_RUN=true ;;
    *) echo "Unknown argument: $arg" >&2; exit 1 ;;
  esac
done

# ── Dependency checks ───────────────────────────────────────────────────────
for cmd in gh python3 jq curl; do
  if ! command -v "$cmd" &>/dev/null; then
    echo "ERROR: '$cmd' is required but not found in PATH." >&2
    exit 1
  fi
done

if [[ "$POST_TO_SLACK" == "true" && -z "${SLACK_BOT_USER_OAUTH_TOKEN:-}" ]]; then
  echo "ERROR: --slack requires SLACK_BOT_USER_OAUTH_TOKEN to be set." >&2
  exit 1
fi

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo " C8 Orchestration Cluster — Local Triage Run"
echo " $(date -u '+%Y-%m-%d %H:%M UTC')"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo

mkdir -p "${ARTIFACTS_DIR}"

# ── Download artifacts ──────────────────────────────────────────────────────
# format: "label|workflow_file|artifact_name"
RUNS=(
  "api-stable-8.7|c8-orchestration-cluster-nightly-8.7-api-es.yml|json-report-nightly-api-stable-8.7"
  "api-stable-8.8|c8-orchestration-cluster-nightly-8.8-api-es.yml|json-report-nightly-api-stable-8.8"
  "api-stable-8.9|c8-orchestration-cluster-nightly-8.9-api-es.yml|json-report-nightly-api-stable-8.9"
  "api-main|c8-orchestration-cluster-nightly-main-api-es.yml|json-report-nightly-api-main"
  "e2e-stable-8.7-v1|c8-orchestration-cluster-nightly-8.7-e2e.yml|json-report-nightly-e2e-stable-8.7-v1"
  "e2e-stable-8.8-v1|c8-orchestration-cluster-nightly-8.8-e2e.yml|json-report-nightly-e2e-stable-8.8-v1"
  "e2e-stable-8.8-v2|c8-orchestration-cluster-nightly-8.8-e2e.yml|json-report-nightly-e2e-stable-8.8-v2"
  "e2e-stable-8.9-v1|c8-orchestration-cluster-nightly-8.9-e2e.yml|json-report-nightly-e2e-stable-8.9-v1"
  "e2e-stable-8.9-v2|c8-orchestration-cluster-nightly-8.9-e2e.yml|json-report-nightly-e2e-stable-8.9-v2"
  "e2e-main-v2|c8-orchestration-cluster-nightly-main-e2e.yml|json-report-nightly-e2e-main-v2"
)

echo "▶ Downloading nightly artifacts from GitHub..."
downloaded=0
skipped=0

for entry in "${RUNS[@]}"; do
  IFS='|' read -r label workflow artifact_name <<< "$entry"
  dest="${ARTIFACTS_DIR}/${label}"

  run_id=$(gh api \
    "/repos/${REPO}/actions/workflows/${workflow}/runs?per_page=1&status=completed&event=schedule" \
    --jq '.workflow_runs[0].id // empty' 2>/dev/null) || true

  if [[ -z "$run_id" ]]; then
    echo "  ⚠  No completed run for ${workflow} — skipped"
    skipped=$((skipped + 1))
    continue
  fi

  artifact_id=$(gh api \
    "/repos/${REPO}/actions/runs/${run_id}/artifacts" \
    --jq ".artifacts[] | select(.name == \"${artifact_name}\") | .id" 2>/dev/null \
    | head -1) || true

  if [[ -z "$artifact_id" ]]; then
    echo "  ⚠  Artifact '${artifact_name}' not in run ${run_id} — skipped"
    skipped=$((skipped + 1))
    continue
  fi

  mkdir -p "$dest"
  gh api "/repos/${REPO}/actions/artifacts/${artifact_id}/zip" > "${dest}/artifact.zip"
  unzip -q "${dest}/artifact.zip" -d "$dest/"
  rm "${dest}/artifact.zip"
  echo "  ✔  ${label} (run ${run_id})"
  downloaded=$((downloaded + 1))
done

echo
echo "  Downloaded: ${downloaded}  |  Skipped: ${skipped}"
echo

if [[ "$downloaded" -eq 0 ]]; then
  echo "ERROR: No artifacts could be downloaded. Check your GH_TOKEN and that nightly runs have completed." >&2
  exit 1
fi

# ── Analyse ─────────────────────────────────────────────────────────────────
echo "▶ Analysing test results with Claude claude-sonnet-4-6..."
if [[ -z "${ANTHROPIC_API_KEY:-}" ]]; then
  echo "ERROR: ANTHROPIC_API_KEY must be set for Claude-powered triage." >&2
  exit 1
fi
python3 "${SCRIPT_DIR}/claude-triage.py" "${ARTIFACTS_DIR}" \
  --spec "${REPO_ROOT}/qa/c8-orchestration-cluster-e2e-test-suite/v2-stateless-tests/request-validation-test-generator/cache/rest-api.yaml" \
  --suite-dir "${REPO_ROOT}/qa/c8-orchestration-cluster-e2e-test-suite" \
  > "${WORK_DIR}/triage-report.json"

# Pretty-print summary
echo
python3 - <<PYEOF
import json, sys

r = json.load(open("${WORK_DIR}/triage-report.json"))
s = r["summary"]

print("  ┌─────────────────────────────────────────┐")
print(f"  │  Total unique failures : {s['total_unique_failures']:<15}│")
print(f"  │  🔨 Test code issues   : {s['test_code']:<15}│")
print(f"  │  🔴 Product regressions: {s['product_regression']:<15}│")
print(f"  │  ⏳ Timing / race cond.: {s['timing']:<15}│")
print(f"  │  ❓ Needs investigation: {s['needs_investigation']:<15}│")
print("  └─────────────────────────────────────────┘")
print()

for hint, icon in [
    ("product_regression",  "🔴 PRODUCT REGRESSION"),
    ("needs_investigation",  "❓ NEEDS INVESTIGATION"),
    ("timing",               "⏳ TIMING / RACE CONDITION"),
    ("test_code",            "🔨 TEST CODE ISSUE"),
]:
    items = [f for f in r["failures"] if f["root_cause_hint"] == hint]
    if not items:
        continue
    print(f"  {icon}  ({len(items)} test(s))")
    for f in items:
        leaf = f["name"].split(" > ")[-1]
        versions = ", ".join(f["versions"])
        print(f"    • {leaf}")
        print(f"      versions : {versions}")
        print(f"      error    : {f['error'][:120]}")
        print(f"      reasons  : {'; '.join(f['root_cause_reasons'])}")
        print()
PYEOF

# Save report to repo root for easy inspection
cp "${WORK_DIR}/triage-report.json" "${REPO_ROOT}/triage-report.json"
echo "  Full report saved to: triage-report.json"
echo

if [[ "$DRY_RUN" == "true" ]]; then
  echo "ℹ  Dry-run mode — skipping Slack post."
  exit 0
fi

# ── Slack post ──────────────────────────────────────────────────────────────
if [[ "$POST_TO_SLACK" == "true" ]]; then
  echo "▶ Posting to Slack..."
  TODAY=$(date -u +"%d.%m.%Y")

  python3 - > "${WORK_DIR}/slack-payload.json" <<PYEOF2
import json, subprocess, os

r = json.load(open("${WORK_DIR}/triage-report.json"))
s = r["summary"]
today = "${TODAY}"

def fmt_failures(hint, limit=8):
    items = [f for f in r["failures"] if f["root_cause_hint"] == hint][:limit]
    if not items:
        return "None ✅"
    return "\n".join(
        f"• \`{f['name'].split(' > ')[-1]}\`"
        f" — {', '.join(f['versions'])}"
        f" — _{f['error_type']}_"
        f"\n  › {'; '.join(f['root_cause_reasons'])}"
        for f in items
    )

total = s["total_unique_failures"]
if total == 0:
    health = "✅ All green"
elif s["product_regression"]:
    health = "🔴 Product regression(s) detected"
elif s["needs_investigation"]:
    health = "🟠 Failures need investigation"
else:
    health = "🟡 Test code / timing issues only"

summary = (
    f"{health}\n"
    f"Total: *{total}* | 🔨 Test code: *{s['test_code']}* | "
    f"🔴 Product: *{s['product_regression']}* | "
    f"⏳ Timing: *{s['timing']}* | ❓ Unknown: *{s['needs_investigation']}*"
)

def section(text):
    return {"type": "section", "text": {"type": "mrkdwn", "text": text}}

payload = {
    "channel": "c8-orchestration-cluster-e2e-test-results",
    "text": f"C8 Orchestration Cluster Triage Report — {today}",
    "blocks": [
        {"type": "header", "text": {"type": "plain_text",
            "text": f"🔬 C8 Orchestration Cluster Triage — {today}"}},
        section(summary),
        {"type": "divider"},
        section(f"*🔨 Test Code Issues*\n{fmt_failures('test_code')}"),
        {"type": "divider"},
        section(f"*🔴 Product Regressions*\n{fmt_failures('product_regression')}"),
        {"type": "divider"},
        section(f"*⏳ Timing / Race Conditions*\n{fmt_failures('timing')}"),
        {"type": "divider"},
        section(f"*❓ Needs Investigation*\n{fmt_failures('needs_investigation')}"),
    ]
}
print(json.dumps(payload))
PYEOF2

  curl -s -X POST https://slack.com/api/chat.postMessage \
    -H "Authorization: Bearer ${SLACK_BOT_USER_OAUTH_TOKEN}" \
    -H "Content-Type: application/json" \
    --data @"${WORK_DIR}/slack-payload.json" \
    | python3 -c "import json,sys; r=json.load(sys.stdin); print('  ✔  Posted to Slack' if r.get('ok') else f'  ✗  Slack error: {r.get(\"error\")}')"
fi
