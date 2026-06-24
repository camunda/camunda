#!/usr/bin/env bash
set -euo pipefail

# Check for jq
if ! command -v jq &> /dev/null; then
    echo "❌ Error: 'jq' is required but not installed"
    echo "   Ubuntu/Debian: sudo apt-get install jq"
    echo "   macOS: brew install jq"
    exit 1
fi

echo "🛠️  VSCode Settings Sync"

# Cleanup temp files on exit
cleanup() {
    rm -f "${USER_SETTINGS}.post-merge"
}
trap cleanup EXIT

# Paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_SETTINGS="${SCRIPT_DIR}/../../.github/settings.json.template"
PROJECT_ROOT="${SCRIPT_DIR}/../.."
USER_SETTINGS="${PROJECT_ROOT}/.vscode/settings.json"

# Ensure project .vscode directory exists
mkdir -p "$(dirname "${USER_SETTINGS}")"

# Create basic user settings file if it doesn't exist
if [[ ! -f "${USER_SETTINGS}" ]]; then
    echo "📝 Creating new settings configuration"
    echo '{}' > "${USER_SETTINGS}"
fi

# Merge configurations
echo "🔍 Checking for changes..."

jq --sort-keys -s '
    .[0] as $user |
    .[1] as $repo |
    $user + $repo
' "${USER_SETTINGS}" "${REPO_SETTINGS}" > "${USER_SETTINGS}.post-merge"

# Check if changes are needed
if git diff --no-index --quiet -- "${USER_SETTINGS}" "${USER_SETTINGS}.post-merge" 2>/dev/null; then
    echo "✅ No changes needed - already up to date"
    exit 0
fi

# Show changes and confirm
echo "📊 Changes:"
git diff --no-index -- "${USER_SETTINGS}" "${USER_SETTINGS}.post-merge" || true

read -p "Apply changes? (y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "❌ Aborted"
    exit 1
fi

# Apply changes
cp "${USER_SETTINGS}" "${USER_SETTINGS}.bak"
mv "${USER_SETTINGS}.post-merge" "${USER_SETTINGS}"

echo "✅ Settings configuration synced!"
echo "💾 Backup: ${USER_SETTINGS}.bak"
echo "💡 Restart VS Code to apply changes"
