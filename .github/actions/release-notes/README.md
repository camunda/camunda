# release-notes action

Shared tooling for the release-notes pipeline (epic
[#53605](https://github.com/camunda/camunda/issues/53605)). One TypeScript
package with two entrypoints that import **one** reference parser:

- `lint/` — the **PR-gate** ([#53593](https://github.com/camunda/camunda/issues/53593)):
  validates that a PR links a tracked issue (or opts out). Live now, **warn-only**.
- `generate/` — the **release-notes generator**
  ([#57713](https://github.com/camunda/camunda/issues/57713)): builds the changelog
  from the PRs shipped in a release range. **Not built yet.**

## Why this exists

Linking a PR to a tracked issue is currently optional and unvalidated, so
features and fixes silently vanish from release notes (epic #53605, root
cause 4). The fix is to enforce the link at PR time using the **exact same
parser** the generator later uses to attribute PRs.

That shared parser is the whole point: if the gate and the generator parsed
differently, a PR could pass the gate and still be mis-attributed by the
generator. Because they import the same module, **a green gate becomes a
structural guarantee** that the generator will attribute the PR correctly.
Do not fork the parser logic — extend it in `src/` and both consumers benefit.

## What the gate does

Given a PR body:

1. Slice the `## Related issues` section (`extractSection`).
2. Extract every reference in it (`parseRefs`): closing keywords
   (`close/closes/closed`, `fix/fixes/fixed`, `resolve/resolves/resolved`),
   the custom `completes #N`, the `Backport of #N` marker, `relates to #N`,
   bare `#N`, `owner/repo#N`, and full GitHub URLs.
3. Resolve each ref against the API (`resolver`): is it an issue, a PR, or
   missing? Is it cross-repo?
4. Decide PASS/FAIL (`policy`).
5. Reconcile a single sticky PR comment (`comment`) explaining the decision.

### Decision table

|                            Situation                             | Result |
|------------------------------------------------------------------|--------|
| Section links a **live issue** via a closing or contributor ref  | ✅ pass |
| Opt-out checkbox ticked (`This PR does not need a linked issue`) | ✅ pass |
| Section links a **pull request** (message names it)              | ❌ fail |
| No satisfying ref **and** no opt-out                             | ❌ fail |

Cross-repo refs (`owner/repo#N`) and the `Backport of #N` marker never satisfy
the requirement on their own. A bare `#N` **does** count (contributor ref).

### Sticky comment

The gate keeps **one** sticky comment per PR, identified by a hidden marker so
re-runs never stack duplicates (`src/comment`):

- **fail** → create the comment (or update the existing one) with the reasons
  and the fix.
- **fixed** (fail → pass) → flip that same comment to a resolved note.
- **never failed** → no comment at all, so the gate stays silent on the ~800
  PRs that already link correctly.

Comment sync is best-effort: an API failure is logged and never fails the gate.
It posts under the app identity (not `GITHUB_TOKEN`) so it triggers the same
downstream automations a human comment would.

## Architecture — pure core + injected IO

```
ParsedRef  ──►  ResolvedRef  ──►  PolicyDecision
(parser)       (resolver)        (policy)
 pure, no IO    the one API call  pure, no IO
```

- `src/parser` and `src/policy` are pure functions — fully unit-tested, no
  network (see `test/`).
- `src/resolver` is the only part that touches the network (one `fetch` to the
  issues API), behind the `Resolver` interface in `src/types.ts`, so the core
  stays testable without mocking everything.
- `src/gha.ts` is a ~40-line shim of the GitHub Actions calls we use, so the
  action ships with **zero runtime dependencies** — the committed bundle is
  entirely our own code.

|      File       |                                      Role                                      |
|-----------------|--------------------------------------------------------------------------------|
| `src/types.ts`  | The `ParsedRef → ResolvedRef → PolicyDecision` contract + `Resolver` interface |
| `src/parser/`   | Pure section-scoped reference extraction (shared with the generator)           |
| `src/policy/`   | Pure PASS/FAIL decision from resolved refs + opt-out state                     |
| `src/resolver/` | GitHub-API adapter (issue vs PR vs missing, cross-repo)                        |
| `src/comment/`  | Sticky-comment render + idempotent upsert (pure logic + `fetch` adapter)       |
| `src/gha.ts`    | Minimal `@actions/core` replacement                                            |
| `src/lint.ts`   | The gate entrypoint (warn-only)                                                |

## Security model

The gate runs on **`pull_request_target`, metadata-only**
(`.github/workflows/release-notes-pr-gate.yml`):

- The PR body is read from the event payload — the workflow **never checks out
  the PR head**. The only checkout takes the base ref (to load this action).
- A privileged token (reused `MONOREPO_RELEASE_APP`) lets later versions post a
  sticky comment and sync the display-only `no-issue` label, including on fork
  PRs. `GITHUB_TOKEN` on plain `pull_request` cannot do that for forks.

Note: because `pull_request_target` uses the workflow definition from the
**base** branch, this workflow only runs once it has landed on the target
branch — the required-check spike can only be verified after merge to `main`.

## Build & test

Node 24. The `dist/` bundle is **committed** (Actions runs it directly, no
runtime install) and kept fresh by CI (`release-notes-action-ci.yml` rebuilds
and diffs it).

```bash
npm ci
npm test        # parser + policy unit tests (node --test via tsx)
npm run typecheck
npm run build   # ncc -> lint/dist/index.js  (commit the result)
```

## Shared constants

`SECTION_HEADING` and `OPT_OUT_PHRASE` in `src/parser/index.ts` are the single
source of truth for the PR template wording. CI greps the template for
`OPT_OUT_PHRASE`, so the parser and `.github/pull_request_template.md` cannot
drift apart.
