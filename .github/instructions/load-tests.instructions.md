---
applyTo: "load-tests/**,.github/workflows/*load-test*,.github/workflows/*load_test*,.github/workflows/*benchmark*,.github/workflows/profile-load-test*,.github/workflows/camunda-*-load-tests*,.github/workflows/camunda-release-load-test*,.github/workflows/camunda-scheduled-release*,.github/workflows/camunda-verify-and-cleanup*,.github/scripts/*load*"
---

# Load Test Review Guidelines

## Required Review Checks

When reviewing changes to load tests, workflows, or load test infrastructure:

1. **Smoke test**: The smoke CI workflow runs automatically on PRs touching
   `load-tests/setup/**` and covers Elasticsearch only. For changes that affect
   other secondary storage types (e.g. OpenSearch) or that may have long-running
   impact, a manual load test run is required in addition to the smoke CI.
2. **Versioned setup folders**: For changes to `load-tests/setup/`, do **not**
   propose backports to stable branches. Instead, apply the same change to every
   relevant versioned subfolder on `main`:
   - `setup/main/` — current unreleased dev version
   - `setup/stable-89/`, `setup/stable-88/`, `setup/stable-87/` — stable versions

   One PR on `main` touching the relevant versioned folders replaces multiple
   backport PRs. Backports remain appropriate only for `load-tests/load-tester/`
   (Java app) and the thin workflow shells on stable branches (see below).

## Documentation

When modifying load test infrastructure, workflows, or setup scripts, always check
if related documentation needs updating:

- `load-tests/README.md` — main entry point and workflow overview
- `load-tests/setup/README.md` — per-namespace folder structure, values
  directory layout, and storage-choice behaviour (primary reference for
  load-test authors)
- Workflow YAML header comments (`.github/workflows/*load-test*`, etc.) — detailed per-workflow reference
- `docs/testing/reliability-testing.md` — goals, test variants, observability, chaos engineering
- This file (`.github/instructions/load-tests.instructions.md`) — AI-facing guidance

## Scheduled Release Load Tests

The file `camunda-scheduled-release-load-tests.yml` uses hardcoded release tags
per stable branch. Patch releases do not require updates since the existing tags
remain valid. When reviewing PRs that create a new minor version (e.g., 8.10) or
deprecate a stable branch, verify that this workflow is updated accordingly.

## Versioned Setup Folders (replaces per-branch backports)

As of issue [#54235](https://github.com/camunda/camunda/issues/54235), cluster
setup configuration lives on `main` only, partitioned by Camunda version:

```
load-tests/setup/
  main/         # current unreleased dev version
  stable-89/    # stable/8.9 setup
  stable-88/    # stable/8.8 setup
  stable-87/    # stable/8.7 setup
  saas-default/ # SaaS/cloud setup — used by newCloudLoadTest.sh only, not versioned
```

**When to update which folder:**

- A change applies to the current dev version only → update `setup/main/`
- A change applies to a stable version → update `setup/stable-8X/`
- A cross-cutting fix (e.g., an Optimize cleanup tweak) → update every affected
  version folder in a single PR on `main`

**What still gets backported (rarely):**

- `load-tests/load-tester/` — Java app source, version-coupled to the branch's
  client library
- Stable-branch workflow files — they must exist on stable branches so
  PR-against-stable triggers fire. Their bodies are thin wrappers: they
  sparse-checkout `load-tests/setup/stable-8X` from `main` and invoke
  `newLoadTest.sh --target-version stable-8X`. Only backport when the
  wrapper shell itself needs changing, not when setup values change.

**What does NOT get backported:**

- Everything under `load-tests/setup/` — Makefiles, values files, secondary
  storage config, `databases/`, `resources/`, scaffolding scripts. These live
  exclusively in the versioned folders on `main`.

### Workflow names by stable branch (for workflow-shell backports only)

- **stable/8.7**: ad-hoc workflow `camunda-load-test.yml`, PR workflow
  `camunda-pr-load-test.yaml`, image build job `build-camunda-image`
- **stable/8.8**: same workflow names as 8.7
- **stable/8.9+ / main**: same workflow names; additionally has cloud load
  test setup scripts and the smoke-dispatch workflow
