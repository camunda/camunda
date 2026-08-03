# AlwaysGreen fix agent — operating manual

Read this in full before touching any file. It is the agent's contract; the dispatch
prompt deliberately carries almost nothing so this stays the single source of truth.

The pipeline is `.github/workflows/docker-build-helm-integration.yml`. On every push it
builds the Camunda image, deploys it to GKE via the Helm charts, runs a Self-Managed
smoke suite, and separately triggers a SaaS smoke suite. `alwaysgreen-triage.yml`
classifies a failure and dispatches you with the specs already extracted.

## Turning it on and off

Controlled by the repository variable `ALWAYSGREEN_FIX_AGENT` (Settings → Variables →
Actions). Changes take effect on the very next run — no PR, no merge, no redeploy.

|   Value   |                               Effect                               |
|-----------|--------------------------------------------------------------------|
| `off`     | triage exits immediately; queued and future fix agents are skipped |
| `dry-run` | triage classifies and reports; nothing is dispatched               |
| `on`      | triage dispatches fix agents                                       |
| *unset*   | treated as `dry-run`                                               |

Unset means `dry-run`, so deleting the variable degrades to safe rather than to live. An
unrecognised value logs a warning and is also treated as `dry-run`.

`off` blocks a fix agent that has been dispatched but not yet started. It does **not**
interrupt a run already inside its Claude step — cancel that run explicitly.

In `dry-run` the job summary shows a "Would dispatch" table, so the classification can be
reviewed without anything being opened.

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

## Where fixes go

|                       Diagnosis                       |           Repository           |                  Path                   |
|-------------------------------------------------------|--------------------------------|-----------------------------------------|
| stale selector, wrong wait, bad assertion             | `c8-cross-component-e2e-tests` | `tests/SM-8.x/`, `tests/8.x/`, `pages/` |
| chart values, Keycloak/Identity wiring, deploy config | `camunda-platform-helm`        | `charts/camunda-platform-8.x/`          |
| product regression                                    | `camunda`                      | the owning module                       |
| pipeline plumbing                                     | `camunda`                      | `.github/`                              |

The spec paths in `/tmp/test_specs.json` are already mapped to source. If you need to
redo it: the suite comes from `config.rootDir` (`…/dist/tests/SM-8.10` → `SM-8.10`) and
the package ships compiled `.js` while the source is `.ts`, so
`smoke-tests.spec.js` → `tests/SM-8.10/smoke-tests.spec.ts`.

The SM smoke suite is **shared** with the SM nightly, which has its own fix agent. A
change there must not regress the nightly for the same version, and a competing
`failing-test-fix` PR may already exist — check before editing.

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
- **Forbidden commands:** `kubectl`, `helm`, any deploy, `npm run build`, `mvnw` full-repo
  builds, and Playwright — there is nothing to run against.

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
