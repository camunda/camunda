#!/usr/bin/env bash
set -euo pipefail

if ! command -v jq &> /dev/null; then
    echo "❌ Error: 'jq' is required but not installed"
    echo "   Ubuntu/Debian: sudo apt-get install jq"
    echo "   macOS: brew install jq"
    exit 1
fi

echo "🤖 Claude Settings Sync"

# Cleanup temp files on exit
cleanup() {
    [[ -n "${USER_SETTINGS:-}" ]] || return 0
    rm -f "${USER_SETTINGS}."{pre,post}"-merge" 2>/dev/null || true
}
trap cleanup EXIT

# Paths — derives Claude permissions from the same VS Code settings template
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_TEMPLATE="${SCRIPT_DIR}/../../.github/settings.json.template"
PROJECT_ROOT="${SCRIPT_DIR}/../.."
USER_SETTINGS="${PROJECT_ROOT}/.claude/settings.json"

mkdir -p "$(dirname "${USER_SETTINGS}")"

# Transform VS Code autoApprove rules to Claude permissions format
# true entries → allow, false entries → deny
# Format: "Bash(command:*)"
repo_claude_settings=$(jq '
  .["chat.tools.terminal.autoApprove"] | to_entries |
  {
    permissions: {
      allow: [.[] | select(.value == true) | "Bash(\(.key):*)"],
      deny:  [.[] | select(.value == false) | "Bash(\(.key):*)"]
    }
  }
' "${REPO_TEMPLATE}")

if [[ ! -f "${USER_SETTINGS}" ]]; then
    echo "📝 Creating .claude/settings.json from repo settings template"
    echo "${repo_claude_settings}" | jq --sort-keys . > "${USER_SETTINGS}"
    echo "✅ Claude settings created!"
    exit 0
fi

echo "🔍 Checking for changes..."

# Union-merge: combine allow/deny arrays from both sources, preserve other top-level keys
jq --sort-keys -s '
  .[0] as $repo | .[1] as $local |
  $local * {
    permissions: {
      allow: ((($repo.permissions.allow // []) + ($local.permissions.allow // [])) | unique | sort),
      deny: ((($repo.permissions.deny // []) + ($local.permissions.deny // [])) | unique | sort)
    }
  }
' <(echo "${repo_claude_settings}") "${USER_SETTINGS}" > "${USER_SETTINGS}.post-merge"

jq --sort-keys . "${USER_SETTINGS}" > "${USER_SETTINGS}.pre-merge"

if git diff --no-index --quiet -- "${USER_SETTINGS}.pre-merge" "${USER_SETTINGS}.post-merge" 2>/dev/null; then
    echo "✅ No changes needed - already up to date"
    exit 0
fi

echo "📊 Changes:"
git diff --no-index -- "${USER_SETTINGS}.pre-merge" "${USER_SETTINGS}.post-merge" || true

read -p "Apply changes? (y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "❌ Aborted"
    exit 1
fi

cp "${USER_SETTINGS}" "${USER_SETTINGS}.bak"
mv "${USER_SETTINGS}.post-merge" "${USER_SETTINGS}"

echo "✅ Claude settings synced!"
echo "💾 Backup: ${USER_SETTINGS}.bak"
