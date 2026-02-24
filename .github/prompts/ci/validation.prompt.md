# CI Validation Protocol

This prompt provides detailed validation procedures for GitHub Actions workflows.

## Mandatory Validation Sequence

### Step 1: Lint First (Non-Negotiable)

**Every workflow change requires clean actionlint results before proceeding.**

```bash
# Run immediately after any workflow modification
actionlint .github/workflows/*.yml
```

**Decision Tree:**
- ✗ Lint fails → Fix errors → Re-lint → Do NOT proceed until clean
- ✓ Lint passes → Continue to Step 2

### Step 2: Policy Compliance

```bash
conftest test --rego-version v0 -o github --policy .github .github/workflows/*.yml
```

### Step 3: Code Formatting

After any changes to files in this repo:

```bash
./mvnw spotless:apply -T1C
```

### Step 4: Assess Testability

**Recommend act testing when:**
- Workflow contains business logic (conditionals, loops, file operations)
- Workflow uses `schedule` or `workflow_dispatch` triggers
- Logic can run safely without external side effects

**Skip act testing when:**
- Simple changes: permission updates, SHA pinning, metadata edits
- Workflow requires `main` branch context or merged state
- Pure external API integrations with no testable internal logic

### Step 5: Act Validation (When Applicable)

```bash
# List available jobs
act --list

# Dry run for structure validation
act --dry-run

# Run specific workflow
act workflow_dispatch -W .github/workflows/<workflow>.yml --verbose
```

## Validation Matrix

|          Scenario          |                     Validation Steps                     |
|----------------------------|----------------------------------------------------------|
| Simple changes             | actionlint + conftest + spotless:apply                   |
| Testable logic             | actionlint + conftest + act --dry-run + targeted act job |
| Untestable (external deps) | actionlint + conftest + document limitation              |

## VS Code Tasks (Preferred)

|               Task                |          Purpose           |
|-----------------------------------|----------------------------|
| `CI: actionlint (workflows)`      | Lint all workflows         |
| `CI: conftest (workflows policy)` | Policy compliance check    |
| `CI: act --list`                  | Inspect available jobs     |
| `CI: act --dry-run`               | Quick structure validation |

