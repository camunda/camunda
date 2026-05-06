---
applyTo: "load-tests/**,.github/workflows/*load-test*,.github/workflows/*load_test*,.github/workflows/*benchmark*,.github/workflows/profile-load-test*,.github/workflows/camunda-*-load-tests*,.github/workflows/camunda-release-load-test*,.github/workflows/camunda-scheduled-release*,.github/workflows/camunda-verify-and-cleanup*,.github/scripts/*load*"
---

# Load Test Review Guidelines

## Required Review Checks

When reviewing changes to load tests, workflows, or load test infrastructure:

1. **Smoke test**: Changes must be smoke tested before merging. Run the affected
   load test at least once to verify it works as expected.
2. **Backport**: Load test changes must be backported to all active maintenance
   branches. Ensure the changes are compatible with each target branch.

## Documentation

When modifying load test infrastructure, workflows, or setup scripts, always check
if related documentation needs updating:

- `load-tests/README.md` — main entry point and workflow overview
- `load-tests/quicker-benchmark/README.md` — `quicker-max` / `quicker-realistic` label flow + per-scenario baseline update procedure
- Workflow YAML header comments (`.github/workflows/*load-test*`, etc.) — detailed per-workflow reference
- `docs/testing/reliability-testing.md` — goals, test variants, observability, chaos engineering
- This file (`.github/instructions/load-tests.instructions.md`) — AI-facing guidance

## Scheduled Release Load Tests

The file `camunda-scheduled-release-load-tests.yml` uses hardcoded release tags
per stable branch. Patch releases do not require updates since the existing tags
remain valid. When reviewing PRs that create a new minor version (e.g., 8.10) or
deprecate a stable branch, verify that this workflow is updated accordingly.

## Backporting Load Test Changes

Stable branches that use the same `load-tests/` directory layout as `main` should
cherry-pick cleanly without path conflicts.

### Other differences by branch

| Feature                               | stable/8.7–8.8      | stable/8.9+ / main  |
|---------------------------------------|---------------------|----------------------|
| Docker image build job in workflows   | `build-zeebe-image` (8.7) / `build-camunda-image` (8.8) | `build-camunda-image`|
| Identity/Optimize/Keycloak in values  | enabled            | enabled              |
| Ad-hoc load test workflow             | `zeebe-benchmark.yml` | `camunda-load-test.yml` |
| PR-triggered load test workflow       | `zeebe-pr-benchmark.yaml` | `camunda-pr-load-test.yaml` |
| Cloud load test setup scripts         | absent              | present              |
