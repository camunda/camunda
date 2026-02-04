#!/bin/bash
# compare-wiki-content.sh - Compare current wiki with migrated docs
# Usage: ./scripts/compare-wiki-content.sh [WIKI_PAGE] [DOCS_FILE]

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Default values (customize these for your migration)
WIKI_PAGE="${1:-Development-Guide}"  # Replace with your wiki page name
DOCS_FILE="${2:-../docs/monorepo-docs/development-guide.md}"  # Your docs file

echo -e "${BLUE}🔄 Fetching current wiki content for: ${CYAN}$WIKI_PAGE${NC}"

# Download wiki content
WIKI_TEMP="/tmp/current-wiki-${WIKI_PAGE}.md"
curl -s "https://raw.githubusercontent.com/wiki/camunda/camunda/${WIKI_PAGE}.md" -o "$WIKI_TEMP"

if [ ! -f "$WIKI_TEMP" ] || [ ! -s "$WIKI_TEMP" ]; then
    echo -e "${RED}❌ Failed to download wiki content. Check the wiki page name.${NC}"
    echo -e "${YELLOW}💡 Try URL encoding special characters (e.g., & becomes %26)${NC}"
    exit 1
fi

echo -e "${BLUE}📋 Comparing files:${NC}"
echo -e "Wiki (current): ${CYAN}$WIKI_TEMP${NC}"
echo -e "Docs (local):   ${CYAN}$DOCS_FILE${NC}"
echo ""

# Check if local file exists
if [ ! -f "$DOCS_FILE" ]; then
    echo -e "${RED}❌ Local file not found: $DOCS_FILE${NC}"
    rm -f "$WIKI_TEMP"
    exit 1
fi

# Skip frontmatter when comparing (first 4 lines typically)
DOCS_TEMP="/tmp/docs-content-only.md"
tail -n +5 "$DOCS_FILE" > "$DOCS_TEMP"

echo -e "${YELLOW}--- DIFFERENCES (colored diff) ---${NC}"

# Use diff with color output if available, otherwise fall back to regular diff
if command -v colordiff &> /dev/null; then
    # Use colordiff if available
    colordiff -u "$WIKI_TEMP" "$DOCS_TEMP" || echo -e "${GREEN}✅ Files are identical!${NC}"
elif diff --color=always --help &> /dev/null; then
    # Use diff with color if supported
    diff --color=always -u "$WIKI_TEMP" "$DOCS_TEMP" || echo -e "${GREEN}✅ Files are identical!${NC}"
else
    # Fall back to regular diff with manual coloring
    diff -u "$WIKI_TEMP" "$DOCS_TEMP" | while IFS= read -r line; do
        case "$line" in
            "+++"*) echo -e "${BLUE}$line${NC}" ;;
            "---"*) echo -e "${BLUE}$line${NC}" ;;
            "@@"*) echo -e "${CYAN}$line${NC}" ;;
            "+"*) echo -e "${GREEN}$line${NC}" ;;
            "-"*) echo -e "${RED}$line${NC}" ;;
            *) echo "$line" ;;
        esac
    done || echo -e "${GREEN}✅ Files are identical!${NC}"
fi

echo ""
echo -e "${YELLOW}💡 For side-by-side comparison in VS Code:${NC}"
echo -e "${CYAN}code --diff $WIKI_TEMP $DOCS_FILE${NC}"

# Cleanup
rm -f "$WIKI_TEMP" "$DOCS_TEMP"