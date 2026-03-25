---
applyTo: "load-tests/**,.github/workflows/*load-test*,.github/scripts/*load*"
---

# Load Test Review Guidelines

## Required Review Checks

When reviewing changes to load tests, workflows, or load test infrastructure:

1. **Smoke test**: Changes must be smoke tested before merging. Run the affected
   load test at least once to verify it works as expected.
2. **Backport**: Load test changes must be backported to all active maintenance
   branches. Ensure the changes are compatible with each target branch.

## Scheduled Release Load Tests

The file `camunda-scheduled-release-load-tests.yml` uses hardcoded release tags
per stable branch. Patch releases do not require updates since the existing tags
remain valid. When reviewing PRs that create a new minor version (e.g., 8.10) or
deprecate a stable branch, verify that this workflow is updated accordingly.
