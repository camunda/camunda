# AlwaysGreen fix agent — operating manual

Read this in full before touching any file. It is the agent's contract; the dispatch
prompt deliberately carries almost nothing so this stays the single source of truth.

More than one pipeline dispatches you, and they share this manual because they share a
shape: on every push they build a product image, deploy it to GKE via the Helm charts,
run a Self-Managed smoke suite, and separately trigger a SaaS smoke suite.
`alwaysgreen-triage.yml` classifies a failure and dispatches you with the specs already
extracted.

| Source pipeline | Repository | Branches |
|---|---|---|
| `docker-build-helm-integration.yml` | `camunda/camunda` | `main`, `stable/8.7`–`8.9` |
| `MERGE_QUEUE_HELM_TEST.yaml` | `camunda/connectors` | `main`, `stable/8.7`–`8.9` |

The dispatch key's first segment names the source (`camunda:main:sm-smoke-e2e`), and the
source repository is the one checked out at the failing branch in your workspace.

## Turning it on and off

A feature flag in Vault: key `ALWAYSGREEN_FIX_AGENT` at
`secret/data/products/qa/ci/common`. Changes take effect on the next run — no PR, no
merge, no redeploy.

|   Value    |                               Effect                               |
|------------|--------------------------------------------------------------------|
| `disabled` | triage exits immediately; queued and future fix agents are skipped |
| `dry-run`  | triage classifies and reports; nothing is dispatched               |
| `enabled`  | triage dispatches fix agents                                       |
| *unset*    | treated as `dry-run`                                               |

Every unresolvable state resolves to `dry-run` — key absent, Vault unreachable, value
unrecognised — so a mistake withholds dispatch rather than enabling it. Two corollaries:
**deleting the key does not stop the agent**, it leaves it classifying, so set `disabled`
explicitly; and matching is exact, so `DISABLED` or `off` fall back to `dry-run`.

`disabled` blocks a fix agent that was dispatched but has not started — its `gate` job
fails the check and the agent job is skipped. It does **not** interrupt a run already
inside its Claude step; cancel that run from the Actions tab.

In `dry-run` the job summary prints a "Would dispatch" table, so the classification can
be reviewed without anything being opened.

The flag is imported as a Vault secret, so GitHub masks its value in the logs of the job
that reads it. The workflows therefore report the derived booleans as `active` and
`dispatch`, and avoid printing the words `enabled`/`disabled`, which a matching flag
value would render as `***`.

## The rule that matters most

**The failing job identifies the surface that broke. It does not identify the repository
that needs the fix.**

Over a 300-run window, the most frequent failure was
`smoke-tests.spec.js › Most Common Flow User Flow With All Apps` timing out on
`locator('#kc-main-content-page-container')` — 19 of 33 real failing jobs. Read as a test
error it looks like a stale selector. The screenshot showed **Keycloak's own error page**:

> We are sorry… Unexpected error when handling authentication request to identity provider.

The assertion was correct. Keycloak was broken. The fix belonged in the Helm/Keycloak
configuration, and raising the timeout would have turned the pipeline green while hiding a
genuinely broken login.

So: establish *what the application did* before deciding the test is wrong.

## Evidence, and where it is

Everything is under `/tmp/alwaysgreen-artifacts/`. There is no live cluster — the
namespace is deleted by the pipeline's cleanup job and the SaaS org is deleted by the
nightly, both before you start. Never try to reach a cluster or run `kubectl`.

|               Artifact               |     Surface      |                         Contains                          |
|--------------------------------------|------------------|-----------------------------------------------------------|
| `playwright-results-json*`           | `sm-smoke-e2e`   | the report, incl. `config.rootDir` and retry history      |
| `playwright-traces*`                 | `sm-smoke-e2e`   | `trace.zip`, `test-failed-1.png`, screenshots per attempt |
| `json-report*`, `Playwright Report*` | `saas-smoke-e2e` | downstream report and HTML report                         |
| `diagnostics-e2e*`                   | `sm-smoke-e2e`   | **namespace dump: describe + logs for every pod**         |

`diagnostics-e2e*` is the one that resolves the Keycloak class. It contains
`Pod: <name> — logs` sections for **all** pods, including Ready ones, so a component that
is running and returning errors is visible. Retention is 1 day, so it may be absent when
replaying an older run — treat absence as "no cluster evidence", not as "the cluster was
fine".

Read PNG screenshots directly. For a trace: `unzip -l trace.zip`, then extract what you need.

## Diagnosis order

1. **Is it flaky?** `/tmp/test_specs.json` carries `attempts` and `statuses` per spec.
   All attempts failed → deterministic, a real defect. `failed → passed` → flaky, and the
   fix is waiting/retry, never a behavioural change. This is decided for you; do not
   re-litigate it with a re-run you cannot perform.
2. **What did the app show?** Open the screenshot. A missing or moved element points at
   the test. An application error page, a 500, or a blank render points at the
   environment or the product.
3. **If the app misbehaved, why?** Read `diagnostics-e2e*` — pod list, events, and the
   logs of the component that erred. This is where a Keycloak stack trace lives.
4. **Does the version matter?** If the same spec passes for another version, the
   difference is real and usually belongs in the product or chart, not the test.
5. **Is the assertion still correct?** For anything about intended behaviour — a default,
   whether a feature exists in this version, eventual-consistency timing — check
   `camunda-docs/versioned_docs/version-<X.Y>/` and cite it in the PR body. Match the
   version tree exactly. Skip this for pure selector drift.

## Regression, or an intended change the test has not caught up with?

A changed locator has two possible causes, and they land in different repositories. Decide
this **before** picking a repo, because guessing wrong is expensive in both directions:
reverting an intentional change destroys someone's work, and adapting the test to a real
regression masks the defect the test exists to catch.

The discriminator is whether the product still agrees with itself. The breaking PR number
is in your prompt — read what it changed:

```bash
gh pr view <blame_pr> --repo camunda/camunda --json title,body,files \
  --jq '{title, files: [.files[].path]}'
```

- **It also updated the product's own tests** to the new value → the change is **intended**
  and the cross-component suite is simply behind. Fix `c8-cross-component-e2e-tests`.
- **Those tests still assert the old value**, so the product now contradicts itself → that
  is a **regression**. Fix `camunda`.

Product tests are not in one place, so grep the PR's file list rather than matching a fixed
glob: colocated `*.test.tsx` next to the component, a `tests/` directory beside it,
`e2e-playwright/` or `e2e/` specs, and `qa/c8-orchestration-cluster-e2e-test-suite/`. In
`operate/client` most are colocated and only a minority sit under `tests/`.

**Absence of a test change only means something if such tests exist.** `identity/client` has
no frontend tests at all, so for an Identity UI failure "the tests were not updated" proves
nothing — skip to the weaker signals below rather than reading it as a regression.

Two weaker signals, for when the first is inconclusive. A Conventional-Commit `feat:` that
renames user-visible copy is usually deliberate, while `refactor:`/`fix:` that changes copy
usually is not. And `camunda-docs` describing the new copy or behaviour for this version
settles it as intended — cite it in the PR body.

If it is still genuinely ambiguous, do **not** pick one. Write `category: "not-determined"`
to `/tmp/fix-meta.json` with the evidence, name both candidate fixes, and leave the
fingerprint unclaimed so a recurrence is re-triaged. A human deciding in ten minutes beats
either wrong PR.

**A test-side fix does not turn the run green by itself.** The helm e2e job runs the
published `@camunda/e2e-test-suite` package, not the repo source, so a locator fix takes
effect only once that package is published. Say so in the PR body: until then the pipeline
stays red and the same failure will be re-dispatched unless your PR carries the coverage
block.

## Where fixes go

|                       Diagnosis                       |           Repository           |                  Path                   |
|-------------------------------------------------------|--------------------------------|-----------------------------------------|
| stale selector, wrong wait, bad assertion             | `c8-cross-component-e2e-tests` | `tests/SM-8.x/`, `tests/8.x/`, `pages/` |
| chart values, Keycloak/Identity wiring, deploy config | `camunda-platform-helm`        | `charts/camunda-platform-8.x/`          |
| product regression                                    | the source repository          | the owning module                       |
| pipeline plumbing                                     | the source repository          | `.github/`                              |

"The source repository" is whichever pipeline dispatched you — `camunda` for
`docker-build-helm-integration.yml`, `camunda/connectors` for
`MERGE_QUEUE_HELM_TEST.yaml`. It is the one repository in the workspace checked out at
the branch that failed, and the dispatch key's first segment names it. When the source
is connectors, read `## When the source is connectors` below before opening a PR there.

The spec paths in `/tmp/test_specs.json` are already mapped to source. If you need to
redo it: the suite comes from `config.rootDir` (`…/dist/tests/SM-8.10` → `SM-8.10`) and
the package ships compiled `.js` while the source is `.ts`, so
`smoke-tests.spec.js` → `tests/SM-8.10/smoke-tests.spec.ts`.

The SM smoke suite is **shared** with the SM nightly, which has its own fix agent. A
change there must not regress the nightly for the same version, and a competing
`failing-test-fix` PR may already exist — check before editing.

## Which version directory to edit

The prompt gives you the resolved directories — use them rather than inferring. The rules
behind them:

- **`main` is the next unreleased minor, currently 8.10.** `stable/X.Y` is X.Y.
- **SM specs live under an `SM-` prefix, SaaS specs under the bare version.** `pages/`
  mirrors `tests/` exactly.
- The Helm chart directory is `charts/camunda-platform-<version>/`.

|             Dispatch             |        e2e specs and pages         |           Helm chart            |
|----------------------------------|------------------------------------|---------------------------------|
| `sm-smoke-e2e` on `main`         | `tests/SM-8.10/`, `pages/SM-8.10/` | `charts/camunda-platform-8.10/` |
| `saas-smoke-e2e` on `main`       | `tests/8.10/`, `pages/8.10/`       | `charts/camunda-platform-8.10/` |
| `sm-smoke-e2e` on `stable/8.9`   | `tests/SM-8.9/`, `pages/SM-8.9/`   | `charts/camunda-platform-8.9/`  |
| `saas-smoke-e2e` on `stable/8.9` | `tests/8.9/`, `pages/8.9/`         | `charts/camunda-platform-8.9/`  |

`stable/8.8` and `stable/8.7` follow the same pattern.

**Do not fan a fix out across version directories.** Only the dispatched version failed;
the differences between version directories often encode real product differences that
should stay encoded, and editing a passing sibling risks breaking it. If the same bug
plausibly affects another version, say so in the PR body instead of changing it.

## When the source is connectors

The run under test built and deployed a fresh connectors image, so a runtime or bundle
regression shows up as a Playwright failure exactly like a stale selector does. Decide
between them the same way as anywhere else — but if you conclude the product is at
fault, these rules are CI-enforced in `camunda/connectors` and a PR that breaks one is
rejected:

- **PR title must match** `^(feat|fix|deps|docs|style|refactor|perf|test|chore|build|other|ci): `.
- **Never `feat:`.** That type requires a named QA approver
  (`ENFORCE_QA_APPROVAL.yml`); an agent PR must not enter that gate. A fix is `fix:`,
  a test change is `test:`, a workflow change is `ci:`.
- **Never hand-edit a generated element template.** They are produced by each
  connector's `GenerateElementTemplate` test, and regenerating one means running Maven,
  which is forbidden. If the fix needs a regenerated template, stop and write
  "no fix determined" with the reason — do not edit the JSON.
- **Never hand cherry-pick a backport.** Each branch's failure is dispatched against
  its own branch, so a backport should not arise. If one genuinely does, say so in the
  PR body and let a human add the `backport stable/X.Y` label
  (`korthout/backport-action`).
- `mvn` and `./mvnw` are forbidden, so you cannot compile or test a Java change. A
  connectors code fix you cannot even compile is a strong signal to escalate instead.

Signals that the fault is connectors rather than the test: the same spec fails on every
branch at once (test-side drift is normally version-scoped), and the blamed PR touches
`connector-runtime/`, `bundle/`, or the connector the spec exercises.

## Which branch the PR targets

A PR in the **source repository** must be opened with `--base <base_ref>` — the branch
that failed, supplied in the prompt. `gh pr create` with no `--base` targets the
repository default branch, so a `stable/8.9` fix opened that way carries the entire
stable-to-main delta, and merging it would push stable-only code onto `main`. The
workflow re-checks the base afterwards and retargets a wrong one, but it warns when it
has to.

This applies to the source repository only. `c8-cross-component-e2e-tests` and
`camunda-platform-helm` have no per-version branches — their PRs take their own default
branch, and the version lives in the path (`tests/SM-8.9/`, `charts/camunda-platform-8.9/`).

## The PR coverage block — mandatory

Every PR you open must carry, in its body:

```
<!-- alwaysgreen-fixed
fp=1a2b3c4d
fp=5e6f7a8b
-->
```

One `fp=` line per fingerprint in `/tmp/fingerprints.json`. Triage reads this block to
suppress re-dispatch. **Omit a fingerprint and the same failure is dispatched again on the
next push.** When updating an existing PR, preserve every line already there — the union,
never a replacement.

Also name the author of the breaking change in the body (supplied in the prompt). The
workflow tries to add them as a reviewer, but that call fails when they are not a
collaborator on the repository you opened the PR in, so the body mention is what
guarantees the signal survives.

## Constraints

- **Never mask a failure.** No timeout increases to paper over a real error, no weakened
  or deleted assertions, no `continue-on-error`, no selector swap that dodges a broken
  page.
- **`test.skip()` / `test.fixme()` / `.only` are forbidden**, except for a confirmed
  product regression that has a filed tracking issue — follow
  `## Product-Bug Escalation` in the e2e repo's `AGENTS.md` and use its annotation format.
- **Minimal diff.** No refactoring, no dependency bumps, nothing unrelated.
- **Fix only the dispatched specs.** Other failures may be visible in the artifacts; leave
  them.
- **Lint before commit.** For `.ts`: `npx prettier --check <files>` and
  `npx eslint <files> --ext .ts`. `printWidth` is 80 and CI fails on a single-character
  formatting delta.
- **Forbidden commands:** `kubectl`, `helm`, any deploy, `npm run build`, `mvn` /
  `./mvnw`, `docker`, and Playwright — there is nothing to run against.

## Result manifest

Write `/tmp/fix-meta.json` before stopping, always:

```json
{
  "surface": "sm-smoke-e2e",
  "category": "test | chart | product | ci | not-determined",
  "prs": [
    {
      "number": 1234,
      "owner": "camunda",
      "repo": "c8-cross-component-e2e-tests",
      "branch": "fix/alwaysgreen-sm-smoke-e2e-keycloak-login",
      "url": "https://github.com/…/pull/1234",
      "root_cause": "One sentence.",
      "fix": "One sentence.",
      "fingerprints": ["1a2b3c4d"]
    }
  ],
  "reason": "Required when prs is empty: what you found and why no change was safe."
}
```

`owner` and `repo` are required — PRs can land in any of the four repositories and the
workflow uses them to label and request review.

**`"prs": []` with `category: "not-determined"` is a legitimate, expected outcome.** If the
evidence shows the environment broke and you cannot pin it to a config change, say so and
attach what you found. That is strictly better than a plausible-looking change that hides
a real defect.
