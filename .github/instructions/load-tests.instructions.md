---
applyTo: "load-tests/**,.github/workflows/*load-test*,.github/workflows/*load_test*,.github/workflows/*benchmark*,.github/workflows/profile-load-test*,.github/workflows/camunda-*-load-tests*,.github/workflows/camunda-release-load-test*,.github/workflows/camunda-scheduled-release*,.github/workflows/camunda-verify-and-cleanup*,.github/scripts/*load*"
---

# Load Test Review Guidelines

## Required Review Checks

When reviewing changes to load tests, workflows, or load test infrastructure:

1. **Smoke test**: The smoke CI workflow runs automatically on PRs touching
   `load-tests/setup/**` and covers Elasticsearch only. For changes that affect
   other secondary storage types (e.g. OpenSearch) or that may have long-running
   impact, a manual load test run is required in addition to smoke CI.

2. **Golden file snapshot tests**: Changes under `load-tests/setup/**` that alter
   the rendered Kubernetes manifests fail the golden file tests in CI. Regenerate
   and commit the golden files in the same PR:
   ```sh
   cd load-tests/setup/test && make update-golden
   ```
   then review `git diff golden/`. See `load-tests/setup/test/README.md` for the
   suite, layout, and how to add a stable version. Or apply the `generate-golden` label to the PR
   and `.github/workflows/load-test-golden-update.yml` will regenerate and commit them
   automatically.

3. **Versioned setup folders**: For changes to `load-tests/setup/`, do **not**
   propose backports to stable branches. Instead, update the relevant versioned
   subfolder(s) on `main`. To find current folders:
   ```sh
   git ls-tree HEAD load-tests/setup/ --name-only
   ```
   Any `stable-*/` subdirectory is a versioned folder; `main/` is the current dev
   version; `saas-default/` is SaaS/cloud only and is not version-partitioned.
   See `load-tests/setup/README.md` for the version-dispatcher pattern and layout.

   When **adding a new versioned folder** (e.g. for a new stable branch), include the
   Renovate annotation immediately above `camunda_platform_helm_chart_version` in the new
   `newLoadTest.sh`:
   ```sh
   # renovate: version=camunda-platform-8.X
   camunda_platform_helm_chart_version="X.Y.Z"
   ```
   This lets Renovate automatically track Helm chart patch updates on that stable branch.
   See `load-tests/setup/README.md#helm-chart-version-management` for full details.

4. **Command-line flag precedence**: A flag added via `+=` to
   `additional_load_test_setup_configuration` or `additional_load_test_configuration` in a
   `load-tests/setup/*/Makefile` is silently dropped when CI passes that same variable on the
   `make` command line (as `camunda-load-test.yml` always does) — GNU Make blocks any later `+=`
   to a variable once it has command-line origin, even if the CLI value is empty
   ([Overriding](https://www.gnu.org/software/make/manual/html_node/Overriding.html),
   [Override Directive](https://www.gnu.org/software/make/manual/html_node/Override-Directive.html)).
   Route makefile-computed defaults through a dedicated accumulator instead (see
   `_load_test_setup_flags` in any versioned Makefile), and verify with:
   ```sh
   make -n <target> ... additional_load_test_setup_configuration="--set foo=bar"
   ```
   A plain `make -n <target>` dry run — or the golden file tests, which never set this variable on
   the command line — will not catch this class of regression.

## What gets backported

**Never backport** (update the versioned folder on `main` instead):
- Everything under `load-tests/setup/` — Makefiles, values files, secondary
  storage config, scripts

**Backport only when the stable-branch artifact itself changes:**
- `load-tests/load-tester/` — Java app; version-coupled to the branch's client library
- `.github/workflows/` on stable branches — thin wrappers that sparse-checkout
  `load-tests/setup/stable-8X` from `main`. Only backport when the wrapper shell
  itself changes, not when setup values change.

## Documentation

When modifying load test infrastructure, workflows, or setup scripts, always check
if related documentation needs updating:

- `load-tests/README.md` — main entry point and workflow overview
- `load-tests/setup/README.md` — version-dispatcher pattern, folder layout, values reference
- `load-tests/setup/test/README.md` — golden file snapshot tests; run `make update-golden` after setup changes
- Workflow YAML header comments (`.github/workflows/*load-test*`, etc.) — per-workflow reference
- `docs/testing/reliability-testing.md` — goals, test variants, observability, chaos engineering
- This file (`.github/instructions/load-tests.instructions.md`) — AI-facing guidance

## Scheduled Release Load Tests

The file `camunda-scheduled-release-load-tests.yml` uses hardcoded release tags
per stable branch. Patch releases do not require updates. When reviewing PRs that
create a new minor version (e.g., 8.10) or deprecate a stable branch, verify that
this workflow is updated accordingly.
