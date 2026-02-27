#!/usr/bin/env bash
set -euo pipefail

# Check for jq
if ! command -v jq &> /dev/null; then
    echo "❌ Error: 'jq' is required but not installed"
    echo "   Ubuntu/Debian: sudo apt-get install jq"
    echo "   macOS: brew install jq"
    exit 1
fi

echo "🛠️  VSCode MCP Sync"

# Cleanup temp files on exit
cleanup() {
    rm -f "${USER_MCP}.post-merge"
}
trap cleanup EXIT

# Paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_MCP="${SCRIPT_DIR}/../../.github/mcp.json.template"
PROJECT_ROOT="${SCRIPT_DIR}/../.."
USER_MCP="${PROJECT_ROOT}/.vscode/mcp.json"

# Ensure project .vscode directory exists
mkdir -p "$(dirname "${USER_MCP}")"

# Create basic user MCP file if it doesn't exist
if [[ ! -f "${USER_MCP}" ]]; then
    echo "📝 Creating new MCP configuration"
    echo '{"servers": {}, "inputs": []}' > "${USER_MCP}"
fi

# Merge configurations
echo "🔍 Checking for changes..."

jq --sort-keys -s '
    .[0] as $user |
    .[1] as $repo |
    {
        "servers": ($user.servers + $repo.servers),
        "inputs": ([$user.inputs[], $repo.inputs[]] | unique)
    }
' "${USER_MCP}" "${REPO_MCP}" > "${USER_MCP}.post-merge"

# Check if changes are needed
if git diff --no-index --quiet -- "${USER_MCP}" "${USER_MCP}.post-merge" 2>/dev/null; then
    echo "✅ No changes needed - already up to date"
    exit 0
fi

# Show changes and confirm
echo "📊 Changes:"
git diff --no-index -- "${USER_MCP}" "${USER_MCP}.post-merge" || true

read -p "Apply changes? (y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "❌ Aborted"
    exit 1
fi

# Apply changes
cp "${USER_MCP}" "${USER_MCP}.bak"
mv "${USER_MCP}.post-merge" "${USER_MCP}"

echo "✅ MCP configuration synced!"
echo "💾 Backup: ${USER_MCP}.bak"
echo "💡 Restart VS Code to apply changes"
