# Creating QA Sub-Issues for Audit Log Epic

## Summary

This document provides a guide for creating QA sub-issues for the Audit Log parent QA epic in the product-hub repository, using the same breakdown structure from the engineering epic.

## What Was Created

A complete set of tools and documentation to facilitate creating 6 QA sub-issues:

```
scripts/
├── README.md                      # Overview of all tools
├── QUICK_REFERENCE.md            # One-page quick start guide
├── QA_EPIC_BREAKDOWN.md          # Detailed breakdown documentation
└── create-qa-epic-subissues.sh   # Interactive creation script
```

## Quick Start

Run this single command to create all QA sub-issues:

```bash
./scripts/create-qa-epic-subissues.sh
```

The script will:
1. Display all 6 issue creation commands
2. Ask if you want to create them now
3. Create the issues if you answer "yes"

## Prerequisites

- GitHub CLI (`gh`) installed: https://cli.github.com
- Authenticated with GitHub: `gh auth login`
- Write access to `camunda/product-hub` repository

## QA Sub-Issues Breakdown

The following 6 QA epics will be created:

| Milestone | Title | Engineering Epic | QA Estimate |
|-----------|-------|------------------|-------------|
| M1 | Core audit log tracking | [#40519](https://github.com/camunda/camunda/issues/40519) | 3-4 weeks |
| M2 | Track process operations | [#40534](https://github.com/camunda/camunda/issues/40534) | 2-3 weeks |
| M3 | Track Identity operations | [#40538](https://github.com/camunda/camunda/issues/40538) | 2-3 weeks |
| M4 | Track user task operations | [#40536](https://github.com/camunda/camunda/issues/40536) | 1-2 weeks |
| M5 | Track resource operations | [#41500](https://github.com/camunda/camunda/issues/41500) | 1-2 weeks |
| M6 | Add operation details (should-have) | [#41196](https://github.com/camunda/camunda/issues/41196) | 1 week |

**Total Estimated QA Effort**: 10-15 weeks (can be parallelized across team)

## References

- **Parent QA Epic**: https://github.com/camunda/product-hub/issues/3178
- **Engineering Epic**: https://github.com/camunda/camunda/issues/39044
- **Product Epic**: https://github.com/camunda/product-hub/issues/1732

## Each QA Epic Includes

- Link to parent QA epic (#3178)
- Link to corresponding engineering epic
- Detailed QA scope breakdown matching engineering tasks
- Test types checklist (unit, integration, E2E, performance, security, acceptance)
- Time estimates
- Appropriate labels (`qa`, `audit-log`, component-specific)

## After Creating Issues

1. **Link them**: Add issue numbers to parent epic #3178 description
2. **Assign**: Assign QA team members
3. **Milestones**: Set target versions (e.g., 8.9.0-alpha3, 8.9.0-alpha4)
4. **Project Board**: Add to QA tracking board
5. **Review**: Validate scope with QA and engineering teams

## Alternative Approaches

### View Commands Only (No Creation)

```bash
echo "no" | ./scripts/create-qa-epic-subissues.sh
```

### Save Commands to File

```bash
echo "no" | ./scripts/create-qa-epic-subissues.sh > qa-issue-commands.txt 2>&1
```

### Manual Creation

Copy individual `gh issue create` commands from the script output and run them manually.

## Detailed Documentation

For comprehensive details, see:
- **Getting Started**: `/scripts/QUICK_REFERENCE.md`
- **Full Breakdown**: `/scripts/QA_EPIC_BREAKDOWN.md`
- **Tool Overview**: `/scripts/README.md`

## Questions or Issues?

- Review the engineering epic for context: https://github.com/camunda/camunda/issues/39044
- Check the script documentation in `/scripts/README.md`
- Consult with QA team leads

---

**Note**: These scripts were created to streamline the QA planning process by automatically generating issues with comprehensive QA scope based on the engineering breakdown. The structure ensures traceability between engineering and QA work.
