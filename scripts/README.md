# QA Epic Sub-Issues Creation Tools

This directory contains scripts and documentation for creating QA sub-issues for the Audit Log feature.

## Files

### 1. `create-qa-epic-subissues.sh` (Executable Script)

Interactive bash script that creates 6 QA sub-issues in the `camunda/product-hub` repository.

**Usage**:
```bash
./scripts/create-qa-epic-subissues.sh
```

**Features**:
- Interactive mode with prompts
- Generates all GitHub CLI commands
- Can create issues automatically or display commands for manual execution
- Includes rate limiting protection (2-second delay between issue creations)
- Color-coded output for better readability

### 2. `QA_EPIC_BREAKDOWN.md` (Documentation)

Comprehensive documentation of the QA epic breakdown structure.

**Contents**:
- Overview of parent epic and engineering epic
- Detailed breakdown for each of the 6 QA milestones (M1-M6)
- Key QA areas for each milestone
- Time estimates
- Labels to apply
- Instructions for using the script
- Post-creation steps
- Test coverage summary
- Total estimated QA effort

### 3. `QUICK_REFERENCE.md` (Quick Start Guide)

One-page quick reference for getting started quickly.

**Contents**:
- TL;DR instructions
- Prerequisites setup
- What the script creates
- Post-creation checklist
- Mapping of QA epics to engineering epics

### 4. `README.md` (This File)

Overview of all files in this directory.

## Background

This tooling was created to address the requirement to create QA sub-issues for the Audit Log parent QA epic using the same breakdown structure from the engineering epic.

**Key Issues**:
- Parent QA Epic: https://github.com/camunda/product-hub/issues/3178
- Engineering Epic: https://github.com/camunda/camunda/issues/39044
- Product Epic: https://github.com/camunda/product-hub/issues/1732

## QA Sub-Issues Created

The script creates 6 QA sub-issues, one for each milestone:

| # | Title | Engineering Epic | Estimate |
|---|-------|------------------|----------|
| 1 | QA: Audit Log M1 - Core audit log tracking | [#40519](https://github.com/camunda/camunda/issues/40519) | 3-4 weeks |
| 2 | QA: Audit Log M2 - Track process operations | [#40534](https://github.com/camunda/camunda/issues/40534) | 2-3 weeks |
| 3 | QA: Audit Log M3 - Track Identity operations | [#40538](https://github.com/camunda/camunda/issues/40538) | 2-3 weeks |
| 4 | QA: Audit Log M4 - Track user task operations | [#40536](https://github.com/camunda/camunda/issues/40536) | 1-2 weeks |
| 5 | QA: Audit Log M5 - Track resource operations | [#41500](https://github.com/camunda/camunda/issues/41500) | 1-2 weeks |
| 6 | QA: Audit Log M6 - Add operation details | [#41196](https://github.com/camunda/camunda/issues/41196) | 1 week (should-have) |

**Total QA Effort**: ~10-15 weeks (can be parallelized)

## Prerequisites

1. **GitHub CLI** installed and configured:
   ```bash
   # Install (macOS)
   brew install gh
   
   # Authenticate
   gh auth login
   ```

2. **Write access** to the `camunda/product-hub` repository

3. **Bash shell** (available by default on macOS/Linux, use Git Bash on Windows)

## Quick Start

1. Navigate to the repository root:
   ```bash
   cd /path/to/camunda/camunda
   ```

2. Run the script:
   ```bash
   ./scripts/create-qa-epic-subissues.sh
   ```

3. Follow the prompts

4. After creation, update the parent epic (#3178) with links to the new sub-issues

## Manual Execution

If you prefer to review the commands before executing:

```bash
# View commands without creating issues
echo "no" | ./scripts/create-qa-epic-subissues.sh

# Save commands to a file
echo "no" | ./scripts/create-qa-epic-subissues.sh > commands.txt 2>&1
```

## Troubleshooting

### Script doesn't execute
```bash
# Make sure the script is executable
chmod +x scripts/create-qa-epic-subissues.sh
```

### GitHub CLI not found
```bash
# Install GitHub CLI
# See: https://cli.github.com
```

### Permission denied
- Ensure you have write access to `camunda/product-hub`
- Re-authenticate: `gh auth login`

### Rate limiting
The script includes a 2-second delay between issue creations. If you still hit rate limits, wait a few minutes and retry.

## Post-Creation Steps

After running the script successfully:

1. **Link to parent epic**: Add the new issue numbers to https://github.com/camunda/product-hub/issues/3178

2. **Assign team members**: Assign QA engineers to each sub-issue

3. **Set milestones**: Align with the engineering epic schedule (8.9.0-alpha3, 8.9.0-alpha4, etc.)

4. **Add to project board**: Track progress on the appropriate project board

5. **Review and refine**: Adjust QA scope based on team feedback

## Support

For questions or issues with these tools:
- Review the detailed documentation in `QA_EPIC_BREAKDOWN.md`
- Check the engineering epic for context: https://github.com/camunda/camunda/issues/39044
- Consult with the QA and engineering teams

## License

These scripts are part of the Camunda repository and follow the same license as the main project.
