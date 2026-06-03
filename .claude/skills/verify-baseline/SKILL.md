---
name: verify-baseline
description: Automates the always-green baseline check before starting any AI-assisted session or code change. Checks CI health on main (default) or a specified base branch (e.g. stable/8.7) and verifies the local build passes. Use before making any code changes.
---

# Verify Baseline

Run this skill before making any code changes to establish a green baseline. It automates the
two-step always-green policy defined in AGENTS.md.

Accepts an optional base branch argument (default: `main`). Pass a different branch when
targeting a stable release, e.g. `/verify-baseline stable/8.7`.

## Step 1 — CI health on the base branch

```bash
gh run list --branch <base-branch> --limit 5 --repo camunda/camunda
```

Where `<base-branch>` is the argument passed to the skill, or `main` if none was given.

- If all recent runs are green: report "CI healthy on `<base-branch>`" and continue.
- If any runs are red: inform the engineer of the failures, note them so they are not confused
  with regressions introduced during the session, and continue — do not block on CI failures
  that may be infrastructure-related.

## Step 2 — Full local build

```bash
./mvnw install -Dquickly -T1C
```

- If the build passes: report "local build green" and continue.
- If the build fails: stop and inform the engineer. Do not proceed until this is green — a
  compilation error here will waste far more time if discovered mid-session.

## Handling test failures

If Step 2 fails due to a test failure:

1. Re-run the failing test once to check if it is flaky.
2. If it passes on retry: treat the baseline as green and proceed.
3. If it fails consistently: stop and inform the engineer — a consistently failing test should not
   be on the base branch. Search for an existing open issue in camunda/camunda. If none exists,
   raise one using `.github/ISSUE_TEMPLATE/bug_report.yml`. Do not disable the test.
