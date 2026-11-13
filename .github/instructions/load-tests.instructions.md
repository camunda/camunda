---
applyTo: "load-tests/**,.github/workflows/*load-test*,.github/workflows/*load_test*,.github/workflows/*benchmark*,.github/workflows/profile-load-test*,.github/workflows/camunda-*-load-tests*,.github/workflows/camunda-release-load-test*,.github/workflows/camunda-scheduled-release*,.github/workflows/camunda-verify-and-cleanup*,.github/scripts/*load*"
---

# Load Test Review Guidelines

## Backports

- `load-tests/load-tester/`: always backport. The Java app is version-coupled to the branch's
  client library. Flag a PR that changes it without a `backport stable/X.Y` label for each
  supported stable branch. A branch may be skipped only when the change cannot apply there (8.7 has
  no REST API, so REST and data availability changes do not apply to it), and the PR description
  must say so. A skipped backport turns the next backport of the same files into a conflict.
- `load-tests/setup/**`: never backport. Update the versioned `stable-*` folder on `main` instead.
- `.github/workflows/` on stable branches (thin wrappers that sparse-checkout
  `load-tests/setup/stable-8X` from `main`): backport only when the wrapper shell itself changes,
  not when setup values change.

## Configuration policy: test what we ship

The general load test setup (`load-tests/setup/*/values/`, `common.mk`,
`load-tests/setup/charts/load-test-setup/`) uses the Camunda defaults. Load test results drive
sizing documentation and performance claims, so a custom setting that flatters the results hides
what customers experience.

- Do not add a setting that deviates from the Camunda default to the general setup. Configure what
  a scenario needs in that scenario, with a clear name (for example a separate leader rebalancing
  test, run with and without).
- A deviation that stays needs a comment with the reason, prefixed `CUSTOMIZATION:` so
  `grep -rn CUSTOMIZATION load-tests/setup` lists every one. When it deviates from the Camunda
  default, it also needs a linked issue that decides whether the setting becomes the default or is
  removed.
- If a load test only works with a custom setting, that setting is a product problem: file an issue
  to change the Camunda default instead of working around it here.
- Flag a PR that adds a deviation without the comment and, where required, the linked issue.

Settings that stay by design (cluster sizing baseline, retention to bound disk usage,
observability flags) still carry the `CUSTOMIZATION:` comment.

## Review checks

1. **Smoke test**: the smoke CI workflow runs on PRs touching `load-tests/setup/**` and covers
   Elasticsearch only. Changes affecting other secondary storage (for example OpenSearch) or with
   long-running impact also need a manual load test run.
2. **Golden files**: a `load-tests/setup/**` change that alters rendered Kubernetes manifests fails
   the golden file tests. Regenerate and commit them in the same PR with
   `cd load-tests/setup/test && make update-golden`, then review `git diff golden/`. Alternatively
   apply the `generate-golden` label to a same-repository PR. Renovate PRs are handled by
   `.github/workflows/load-test-golden-update.yml`. Details: `load-tests/setup/test/README.md`.
3. **Versioned setup folders**: `stable-*/` folders are versioned, `main/` is the current dev
   version, and `saas-default/` is cloud-only and not version-partitioned. List them with
   `git ls-tree HEAD load-tests/setup/ --name-only`. A new versioned folder needs this annotation
   directly above `camunda_platform_helm_chart_version` in `load-tests/setup/newLoadTest.sh`, so Renovate tracks
   chart patch updates:
   ```sh
   # renovate: version=camunda-platform-8.X
   camunda_platform_helm_chart_version="X.Y.Z"
   ```
   Details: `load-tests/setup/README.md`.
4. **Makefile flag precedence**: a flag added with `+=` to
   `additional_load_test_setup_configuration` or `additional_load_test_configuration` in a
   `load-tests/setup/*/Makefile` is silently dropped when CI passes the same variable on the `make`
   command line, as `camunda-load-test.yml` always does. GNU Make ignores later `+=` once a variable
   has command-line origin, even when the value is empty. Route computed defaults through a
   dedicated accumulator (see `_load_test_setup_flags` in `load-tests/setup/common.mk`) and verify with
   `make -n <target> ... additional_load_test_setup_configuration="--set foo=bar"`. A plain
   `make -n <target>` or the golden file tests do not catch this.
5. **Client usage in `load-tests/load-tester`**: read queries must use the
   `@Qualifier("queryCamundaClient")` client. The default `CamundaClient` is only for generating
   load. Flag a read query sent through the default client.
6. **Scheduled release load tests**: `camunda-scheduled-release-load-tests.yml` hardcodes release
   tags per stable branch. Patch releases need no update. A PR that creates a new minor version or
   deprecates a stable branch must update this workflow.

## Scripts and k6

- Adding or extending load-test tooling (metrics, reporting, profiling, ops helpers): Python by default for
  non-trivial scripts, shell only for thin wrappers and glue, Go for a maintained binary, stronger
  compile-time guarantees, parallelism, or a real multi-command CLI. See
  `load-tests/docs/scripts/README.md`.
- After any change under `load-tests/setup/charts/load-test-setup/k6/scripts/`, run
  `make -C load-tests/setup/charts/load-test-setup/k6 test` and
  `make -C load-tests/setup/charts/load-test-setup/k6 fmt`, and update `k6/tests/`
  when behavior changes.

## Documentation

When changing load test infrastructure, workflows, or setup scripts, check whether these need an
update:

- `load-tests/README.md`: entry point and workflow overview
- `load-tests/setup/README.md`: version-dispatcher pattern, folder layout, values reference
- `load-tests/setup/test/README.md`: golden files; run `make update-golden` after setup changes
- Workflow YAML header comments: per-workflow reference
- `docs/testing/reliability-testing.md`: goals, test variants, observability, chaos engineering
- camunda-docs
  [`sizing-self-managed.md`](https://github.com/camunda/camunda-docs/blob/main/docs/components/best-practices/architecture/sizing-self-managed.md):
  its "Baseline resource configuration" table quotes values from
  `load-tests/setup/charts/load-test-setup/values.yaml` (Elasticsearch CPU, memory, disk) and
  `load-tests/setup/<branch>/values/camunda-platform-values-defaults.yaml` (Orchestration Cluster
  CPU, memory, disk). Changing them needs a matching camunda-docs PR, for `docs/` and for the
  versioned copy of each affected stable branch.
