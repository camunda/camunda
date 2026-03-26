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

## Backporting Load Test Changes

The load test infrastructure was restructured in 8.9. When backporting changes to
stable/8.6, 8.7, or 8.8, you must adapt paths and references manually.

### Directory mapping

| Component            | stable/8.6, 8.7, 8.8          | stable/8.9+ / main       |
|----------------------|--------------------------------|--------------------------|
| Helm values files    | `zeebe/benchmarks/`            | `load-tests/`            |
| Load tester code     | `zeebe/benchmarks/project/`    | `load-tests/load-tester/`|
| Setup scripts        | `zeebe/benchmarks/setup/`      | `load-tests/setup/`      |
| Docs                 | `zeebe/benchmarks/docs/`       | `load-tests/docs/`       |

### Other differences by branch

| Feature                               | stable/8.6–8.7     | stable/8.8          | stable/8.9+ / main  |
|---------------------------------------|---------------------|---------------------|----------------------|
| Docker image build job in workflows   | `build-zeebe-image` | `build-camunda-image`| `build-camunda-image`|
| Identity/Optimize/Keycloak in values  | disabled            | disabled            | enabled              |
| Ad-hoc load test workflow             | `zeebe-benchmark.yml` | `zeebe-benchmark.yml` | `camunda-load-test.yml` (renamed) |
| PR-triggered load test workflow       | `zeebe-pr-benchmark.yaml` | `zeebe-pr-benchmark.yaml` | `camunda-pr-load-test.yaml` (renamed) |
| Cloud load test setup scripts         | absent              | absent              | present              |

Cherry-picks from main to 8.6–8.8 will hit modify/delete conflicts because files
were renamed. Always resolve by applying the intended change to the correct path
on the target branch rather than accepting the cherry-pick output as-is.
