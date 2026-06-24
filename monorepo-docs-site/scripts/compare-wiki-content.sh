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
# Sanitize wiki page name for safe filename (replace invalid chars with underscore)
SAFE_WIKI_NAME="${WIKI_PAGE//[^a-zA-Z0-9_-]/_}"
WIKI_TEMP=$(mktemp "/tmp/wiki-${SAFE_WIKI_NAME}-XXXXXX.md")
echo -e "${YELLOW}📥 Downloading from: ${CYAN}https://raw.githubusercontent.com/wiki/camunda/camunda/${WIKI_PAGE}.md${NC}"

if ! curl -f -s "https://raw.githubusercontent.com/wiki/camunda/camunda/${WIKI_PAGE}.md" -o "$WIKI_TEMP"; then
    echo -e "${RED}❌ Failed to download wiki content. Possible issues:${NC}"
    echo -e "${YELLOW}  • Wiki page doesn't exist: ${WIKI_PAGE}${NC}"
    echo -e "${YELLOW}  • Special characters need URL encoding (& → %26)${NC}"
    echo -e "${YELLOW}  • Network connectivity issues${NC}"
    echo -e "${YELLOW}💡 Try visiting the URL in your browser to verify it exists${NC}"
    exit 1
fi

if [ ! -s "$WIKI_TEMP" ]; then
    echo -e "${RED}❌ Downloaded file is empty${NC}"
    rm -f "$WIKI_TEMP"
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

# Skip frontmatter if present (lines between leading '---' delimiters)
DOCS_TEMP=$(mktemp "/tmp/docs-content-XXXXXX.md")
if head -1 "$DOCS_FILE" | grep -q '^---'; then
    # Has YAML frontmatter: strip everything up to and including the closing '---'
    awk '/^---/{if(++c==2){found=1; next}} found' "$DOCS_FILE" > "$DOCS_TEMP"
else
    # No frontmatter: compare the full file
    cp "$DOCS_FILE" "$DOCS_TEMP"
fi

echo -e "${YELLOW}--- DIFFERENCES (colored diff) ---${NC}"

# Check for colordiff, install if needed
if ! command -v colordiff &> /dev/null; then
    echo -e "${YELLOW}📦 Installing colordiff for better output...${NC}"
    if command -v apt-get &> /dev/null; then
        sudo apt-get install -y colordiff
    elif command -v brew &> /dev/null; then
        brew install colordiff
    elif command -v yum &> /dev/null; then
        sudo yum install -y colordiff
    else
        echo -e "${RED}❌ Cannot install colordiff automatically. Please install it manually:${NC}"
        echo -e "${CYAN}  - Ubuntu/Debian: sudo apt-get install colordiff${NC}"
        echo -e "${CYAN}  - macOS: brew install colordiff${NC}"
        echo -e "${CYAN}  - RHEL/CentOS: sudo yum install colordiff${NC}"
        exit 1
    fi
fi

# Use colordiff for clean, colored output
DIFF_EXIT_CODE=0
colordiff -u "$WIKI_TEMP" "$DOCS_TEMP" || DIFF_EXIT_CODE=$?

case $DIFF_EXIT_CODE in
    0) echo -e "${GREEN}✅ Files are identical!${NC}" ;;
    1) echo -e "${YELLOW}📝 Differences found above${NC}" ;;
    *) echo -e "${RED}❌ Error occurred during comparison${NC}" ;;
esac

# Cleanup
rm -f "$WIKI_TEMP" "$DOCS_TEMP"