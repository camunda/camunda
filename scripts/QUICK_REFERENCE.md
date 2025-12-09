# Quick Reference: QA Epic Sub-Issues Creation

## TL;DR

Run this command to create all QA sub-issues for the Audit Log QA Epic:

```bash
./scripts/create-qa-epic-subissues.sh
```

When prompted, type `yes` to create the issues or `no` to just view the commands.

## What This Does

Creates 6 QA sub-issues in `camunda/product-hub` repository:

1. **QA: Audit Log M1 - Core audit log tracking** (3-4 weeks)
2. **QA: Audit Log M2 - Track process operations** (2-3 weeks)
3. **QA: Audit Log M3 - Track Identity operations** (2-3 weeks)
4. **QA: Audit Log M4 - Track user task operations** (1-2 weeks)
5. **QA: Audit Log M5 - Track resource operations** (1-2 weeks)
6. **QA: Audit Log M6 - Add operation details** (1 week, should-have)

All issues will:
- Link to parent QA epic: https://github.com/camunda/product-hub/issues/3178
- Link to corresponding engineering epic
- Include detailed QA scope breakdown
- Have appropriate labels (`qa`, `audit-log`, etc.)

## Prerequisites

```bash
# Install GitHub CLI
brew install gh  # macOS
# OR see https://cli.github.com for other platforms

# Authenticate
gh auth login

# Verify access to product-hub
gh repo view camunda/product-hub
```

## After Creating Issues

1. Link them to parent epic #3178
2. Assign QA team members
3. Set milestones if applicable
4. Add to project board

## Manual Creation

If you prefer to create issues manually, view the commands:

```bash
echo "no" | ./scripts/create-qa-epic-subissues.sh
```

Then copy and run each `gh issue create` command individually.

## File Locations

- Script: `/scripts/create-qa-epic-subissues.sh`
- Detailed breakdown: `/scripts/QA_EPIC_BREAKDOWN.md`
- This guide: `/scripts/QUICK_REFERENCE.md`

## Mapping to Engineering Epics

| QA Epic | Engineering Epic | Components |
|---------|------------------|------------|
| M1 | [#40519](https://github.com/camunda/camunda/issues/40519) | Core, API, Client, Zeebe |
| M2 | [#40534](https://github.com/camunda/camunda/issues/40534) | Process Operations |
| M3 | [#40538](https://github.com/camunda/camunda/issues/40538) | Identity |
| M4 | [#40536](https://github.com/camunda/camunda/issues/40536) | User Tasks, Tasklist |
| M5 | [#41500](https://github.com/camunda/camunda/issues/41500) | Resources, Operate |
| M6 | [#41196](https://github.com/camunda/camunda/issues/41196) | Operation Details (optional) |

## Questions?

See detailed breakdown in `QA_EPIC_BREAKDOWN.md` or review the parent engineering epic:
https://github.com/camunda/camunda/issues/39044
