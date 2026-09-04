---
name: new-stable-load-test
description: Configures a new Camunda stable branch into the load test workflows.
---

# Configuring load test for a new stable branch

The full step-by-step checklist to configure a newly `stable/X.Y` branch into
the load test workflows, with linked reference PRs for every step, lives in
[`load-tests/docs/new-stable-branch.md`](../../load-tests/docs/new-stable-branch.md).

Use this skill when a new `stable/X.Y` branch was just branched off the `main`
branch and the new branch needs to be reconfigured to run as a "stable" branch,
rather than as the "main" branch.

## Naming conventions

The doc uses different variables to reference the new stable branch version, in different contexts.
Make sure to interpolate these variables with the correct values.

## How to proceed

1. Read `load-tests/docs/new-stable-branch.md` in full before making any change. It lists the
   exact files to edit, in order, split into different sections. Each section states the
   observable result to check before moving on to the next one — the doc itself only states the
   file to change, not the effect, so verify against that stated result rather than the diff alone.
2. ["On the new stable branch"](../../../load-tests/docs/new-stable-branch.md#on-the-new-stable-branch)
   and ["On `main`"](../../../load-tests/docs/new-stable-branch.md#on-main) are two independent
   changes against two different base branches. Open them as two separate PRs;
   they can be done in either order.
3. Use the linked reference PRs in the doc as the template for each change: they show the exact
   diff shape for that step on a prior branch (8.10). Those links pin an old commit — confirm the
   referenced file/line still exists in the current version before matching
   against it. The code from the `main` branch may also have changed since
   these reference PRs.
