#!/bin/bash
# compare-wiki-content.sh - Compare current wiki with migrated docs
# Usage: ./scripts/compare-wiki-content.sh [WIKI_PAGE] [DOCS_FILE]

# Default values (customize these for your migration)
WIKI_PAGE="${1:-Development-Guide}"  # Replace with your wiki page name
DOCS_FILE="${2:-../docs/monorepo-docs/development-guide.md}"  # Your docs file

echo "🔄 Fetching current wiki content for: $WIKI_PAGE"
curl -s "https://raw.githubusercontent.com/wiki/camunda/camunda/${WIKI_PAGE}.md" \
  -o "/tmp/current-wiki-${WIKI_PAGE}.md"

echo "📋 Comparing wiki vs documentation:"
echo "Wiki (current): /tmp/current-wiki-${WIKI_PAGE}.md"
echo "Docs (local):   $DOCS_FILE"
echo ""

# Skip frontmatter when comparing (first 4 lines typically)
echo "--- DIFFERENCES FOUND ---"
tail -n +5 "$DOCS_FILE" > "/tmp/docs-content-only.md"
diff -u "/tmp/current-wiki-${WIKI_PAGE}.md" "/tmp/docs-content-only.md" || echo "Files are identical!"

# Cleanup
rm "/tmp/current-wiki-${WIKI_PAGE}.md" "/tmp/docs-content-only.md"