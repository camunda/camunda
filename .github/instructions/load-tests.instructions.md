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
