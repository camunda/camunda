#!/usr/bin/env bash
set -euo pipefail

if ! command -v jq &> /dev/null; then
    echo "❌ Error: 'jq' is required but not installed"
    echo "   Ubuntu/Debian: sudo apt-get install jq"
    echo "   macOS: brew install jq"
    exit 1
fi

echo "🤖 Claude MCP Sync"

# Cleanup temp files on exit
cleanup() {
    [[ -n "${USER_MCP:-}" ]] || return 0
    rm -f "${USER_MCP}."{pre,post}"-merge" 2>/dev/null || true
}
trap cleanup EXIT

# Paths — derives Claude config from the same VS Code MCP template
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_TEMPLATE="${SCRIPT_DIR}/../../.github/mcp.json.template"
PROJECT_ROOT="${SCRIPT_DIR}/../.."
USER_MCP="${PROJECT_ROOT}/.mcp.json"

# Transform VS Code format {"servers": {...}} to Claude format {"mcpServers": {...}}
# Exclude "github" server — it requires Copilot auth; Claude uses `gh` CLI instead.
repo_claude_mcp=$(jq '{mcpServers: (.servers | del(.github))}' "${REPO_TEMPLATE}")

if [[ ! -f "${USER_MCP}" ]]; then
    echo "📝 Creating .mcp.json from repo MCP template"
    echo "${repo_claude_mcp}" | jq --sort-keys . > "${USER_MCP}"
    echo "✅ Claude MCP config created!"
    exit 0
fi

echo "🔍 Checking for changes..."

# Merge: repo mcpServers take precedence, user's custom servers preserved, github always removed
jq --sort-keys -s '
    .[0] as $user | .[1] as $repo |
    ($user * {mcpServers: (($user.mcpServers // {}) + $repo.mcpServers)})
    | del(.mcpServers.github)
' "${USER_MCP}" <(echo "${repo_claude_mcp}") > "${USER_MCP}.post-merge"
jq --sort-keys . "${USER_MCP}" > "${USER_MCP}.pre-merge"

if git diff --no-index --quiet -- "${USER_MCP}.pre-merge" "${USER_MCP}.post-merge" 2>/dev/null; then
    echo "✅ No changes needed - already up to date"
    exit 0
fi

echo "📊 Changes:"
git diff --no-index -- "${USER_MCP}.pre-merge" "${USER_MCP}.post-merge" || true

read -p "Apply changes? (y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "❌ Aborted"
    exit 1
fi

cp "${USER_MCP}" "${USER_MCP}.bak"
mv "${USER_MCP}.post-merge" "${USER_MCP}"

echo "✅ Claude MCP config synced!"
echo "💾 Backup: ${USER_MCP}.bak"
