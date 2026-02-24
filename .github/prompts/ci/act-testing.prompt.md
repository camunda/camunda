# Act Testing Guide

This prompt provides detailed guidance for creating act-testable workflow scenarios.

## When to Create Act Tests

**Recommend testing when:**
- Workflow contains business logic (conditionals, loops, data transforms)
- Workflow uses `schedule` or `workflow_dispatch` triggers
- Logic can run safely without external side effects
- Workflow doesn't perform destructive actions

**Skip testing when:**
- Simple changes: permission updates, SHA pinning, metadata edits
- Workflow requires `main` branch context
- Pure external API integrations

## Test Workflow Creation Pattern

Create test workflows at `.github/workflows/test-<workflow-name>.yml`

### Key Principles

**✅ DO - Mirror production logic:**
- Copy exact business logic, algorithms, control flow
- Preserve actual variable names, conditionals, loops
- Keep same error handling and edge cases
- Use identical shell scripting patterns

**✅ DO - Mock external dependencies:**
- Replace GitHub API calls with static mock data files
- Replace git operations with echo statements
- Replace external service calls with logged mock responses

**❌ DON'T - Oversimplify:**
- Don't replace complex algorithms with simple echo statements
- Don't skip testing loops, conditionals, data transformations
- Don't test only "happy path" - include edge cases

### Example Transformation

```yaml
# PRODUCTION WORKFLOW (real)
- name: Find orphaned directories
  run: |
    while IFS= read -r dir; do
      pr_num=$(echo "$dir" | sed 's/pr-//')
      if ! grep -q "^${pr_num}$" open_prs.txt; then
        orphaned_dirs="${orphaned_dirs}${dir} "
      fi
    done < preview_dirs.txt

# TEST WORKFLOW (mocked data, same logic)
- name: Find orphaned directories (REAL LOGIC)
  run: |
    # Create mock data files
    echo -e "pr-123\npr-456" > preview_dirs.txt
    echo "456" > open_prs.txt
    
    # THE ACTUAL PRODUCTION LOGIC (identical)
    while IFS= read -r dir; do
      pr_num=$(echo "$dir" | sed 's/pr-//')
      if ! grep -q "^${pr_num}$" open_prs.txt; then
        orphaned_dirs="${orphaned_dirs}${dir} "
      fi
    done < preview_dirs.txt
    
    echo "Orphaned: $orphaned_dirs"
```

### Test Workflow Template

```yaml
name: Test - <Original Workflow Name>
on:
  workflow_dispatch:
    inputs:
      dry_run:
        description: 'Test dry run mode'
        type: boolean
        default: true

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup mock data
        run: |
          # Create mock files that mirror real data structure
          echo "mock data" > mock_input.txt
      
      - name: Run actual logic (mirrored from production)
        run: |
          # PASTE EXACT PRODUCTION LOGIC HERE
          # Only external calls are mocked
```

## Test Runner Script Template

```bash
#!/usr/bin/env bash
set -euo pipefail

echo "🧪 Testing <Workflow Name> - REAL LOGIC VALIDATION"
echo "=================================================="

# Prerequisites check
command -v act >/dev/null 2>&1 || { echo "❌ act not installed. Run: brew install act"; exit 1; }
docker info >/dev/null 2>&1 || { echo "❌ Docker not running."; exit 1; }

echo "✓ Prerequisites met"
echo "🔒 Safety: act runs in isolated Docker containers"
echo ""

echo "🎯 TESTING SCOPE:"
echo "• [List specific algorithms being tested]"
echo "• [List data transformations being tested]"
echo ""

# Run the test
act workflow_dispatch -W .github/workflows/test-<workflow-name>.yml --verbose

echo ""
echo "📋 Test Results Interpretation:"
echo "✅ SUCCESS: [Expected output markers]"
echo "⚠️  MOCKED: [Components that show simulation messages]"
```

## Act Limitations

Act runs in **isolated Docker containers**:
- ✓ File operations, scripts, logic execute realistically
- ✓ Environment variables and job outputs work
- ✗ Changes don't persist to real repository
- ✗ GitHub API calls won't work without tokens
- ✗ Vault/external service auth will fail

**Default approach for untestable dependencies:**
- Replace with `echo` statements logging expected behavior
- Document which steps are mocked and why
- Ensure testable business logic still executes
