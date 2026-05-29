#!/usr/bin/env bash
# Verifies prerequisites for repo-level Claude Code skills.
set -euo pipefail

ok=true

echo "Checking prerequisites for .claude/skills..."
echo ""

if ! command -v gh &>/dev/null; then
  echo "  [MISSING] gh — install from https://cli.github.com"
  ok=false
else
  if gh auth status &>/dev/null; then
    echo "  [OK]      gh (authenticated)"
  else
    echo "  [ERROR]   gh is installed but not authenticated — run: gh auth login"
    ok=false
  fi
fi

if ! command -v jq &>/dev/null; then
  echo "  [MISSING] jq — brew install jq  /  apt-get install jq"
  ok=false
else
  echo "  [OK]      jq"
fi

echo ""
echo "Available skills:"
for dir in "$(dirname "$0")"/*/; do
  skill="$(basename "$dir")"
  desc=$(grep -m1 '^description:' "$dir/SKILL.md" 2>/dev/null | sed 's/^description: //' || echo "(no description)")
  printf "  %-30s %s\n" "$skill" "$desc"
done

echo ""
if $ok; then
  echo "All prerequisites satisfied."
else
  echo "Fix the issues above before using the skills."
  exit 1
fi
