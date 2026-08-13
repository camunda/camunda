# release-notes action

Shared tooling for the release-notes pipeline (epic
[#53605](https://github.com/camunda/camunda/issues/53605)). One TypeScript
package with two entrypoints that import **one** reference parser:

- `lint/` — the **PR-gate** ([#53593](https://github.com/camunda/camunda/issues/53593)):
  validates that a PR links a tracked issue (or opts out) and lints its title;
  syncs a sticky comment and a `no-issue` label. Live now, **warn-only**.
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

The gate runs two checks (`gate.evaluateGate`) and reports a combined outcome.

**PR-issue link** — given a PR body:

1. Slice the `## Related issues` section (`extractSection`).
2. Extract every reference in it (`parseRefs`): closing keywords
   (`close/closes/closed`, `fix/fixes/fixed`, `resolve/resolves/resolved`),
   the custom `completes #N`, the `Backport of #N` marker, `relates to #N`,
   bare `#N`, `owner/repo#N`, and full GitHub URLs.
3. Resolve each ref against the API (`resolver`): is it an issue, a PR, or
   missing? Is it cross-repo?
4. Decide PASS/FAIL (`policy`).

**Title** — lint the PR title against the active `commitlint.config.cjs` rules
(`title`): `type: subject` shape, `type` in the enum, lower-case, no scope,
header ≤ length. Skipped for bot authors (D16); their link/marker is still
validated. See [Title lint](#title-lint) and [Bot exemptions](#bot-exemptions).

Then it reconciles a single sticky PR comment (`comment`) covering both checks,
and syncs the display-only `no-issue` label (`labels`) to the PR-issue-link
check alone.

### Decision table (PR-issue link)

|                             Situation                             | Result |
|-------------------------------------------------------------------|--------|
| Section links a **live issue** via a closing or contributor ref   | ✅ pass |
| Opt-out checkbox ticked (`This PR does not need a linked issue`)  | ✅ pass |
| **Backport** whose original PR is properly attributed (hop)       | ✅ pass |
| Author is an exempt bot (`renovate[bot]`) and nothing else passed | ✅ pass |
| Section links a **pull request** (message names it)               | ❌ fail |
| No satisfying ref **and** no opt-out                              | ❌ fail |

Cross-repo refs (`owner/repo#N`) never satisfy on their own. A bare `#N` **does**
count (contributor ref).

### Bot exemptions

Two separate sets in `src/title`, and they must stay separate:

- `BOT_TITLE_EXEMPT` — skips the **title** check for bots whose titles are
  machine-generated (D16). Their link/marker is still validated.
- `BOT_LINK_EXEMPT` — skips the **link** check. Currently `renovate[bot]` only:
  it opens PRs from its own template and will never tick the opt-out, and
  dependency bumps are not release-notes material.

`BOT_LINK_EXEMPT` must **never** be replaced by `BOT_TITLE_EXEMPT`. That set
contains `monorepo-devops-automation[bot]`, the author of every backport PR;
exempting it from the link check would skip the [backport hop](#backport-hop), so
backports would stop inheriting their original's issue and silently drop out of
the release notes — the exact failure this gate exists to prevent.

The link exemption is applied **after** the hop and only to a still-failing
link, so it is a fallback and never a bypass: a bot PR that does link an issue
keeps its real policy code (`section-closing`, not `bot-exempt`).

### Backport hop

A backport PR passes on its own `## Related issues` section if it has one
(manual backport template). Otherwise — the backport **bot** body carries only
`⤵️ Backport of #N`, no section — the gate follows that marker to the **original
PR** and validates *its* attribution (`deliveryPath: backportHop`). The backport
inherits the original's linked issue (C7); the `Backport of #N` marker is never
removed (the stale-backport tracker depends on it).

The hop only fires when the link is otherwise **undeclared** — a section that
links a pull request is a hard error and is *not* rescued by an unrelated
`Backport of #N`. When the marker cannot be followed to an original PR the
message names why: it points at an **issue** (not a PR), it **doesn't resolve**
to anything in this repo, or it's **cross-repo** (`owner/other#N`, which this
action can't validate) — rather than folding into the generic "no linked issue"
message.

HTML comments are stripped before parsing, so the PR template's own instructional
`<!-- … closes #1234 … -->` boilerplate is never mistaken for a real ref.

### Title lint

The title check reimplements the **active** `commitlint.config.cjs` rules
(`type-empty`, `type-case`, `type-enum`, `scope-empty`, `header-max-length`) as
a pure regex, so the action keeps **zero runtime dependencies** rather than
vendoring `@commitlint` into the committed bundle. `TITLE_TYPES` and `HEADER_MAX`
in `src/title` are the source of truth, and the action CI greps
`commitlint.config.cjs` to fail if they ever drift.

### Sticky comment

The gate keeps **one** sticky comment per PR, identified by a hidden marker so
re-runs never stack duplicates (`src/comment`):

- **fail** → create the comment (or update the existing one) with the reasons
  and the fix.
- **fixed** (fail → pass) → flip that same comment to a resolved note.
- **never failed** → no comment at all, so the gate stays silent on the ~800
  PRs that already link correctly.

The body is deliberately terse — the failing reasons and a link to
[Causes and fixes](https://camunda.github.io/camunda/ci/#release-notes-pr-gate).
Everything else (why the rule exists, the full cause list, the rollout state)
lives in the docs rather than being restated on every failing PR.

Comment sync is best-effort: an API failure is logged and never fails the gate.
It posts with `GITHUB_TOKEN` as `github-actions[bot]` — nothing reacts to this
comment as an event, so no App identity (and therefore no Vault secret) is
needed. Skipped on fork PRs; see [Fork pull requests](#fork-pull-requests).

### `no-issue` label

The gate also syncs a single label, `no-issue` (`src/labels`), mirroring the
**PR-issue-link check only** — a title-only failure never touches it:

- link check **fails** and the label is absent → add it.
- link check **passes** and the label is present → remove it.
- otherwise → no-op.

This runs during warn-only rollout too (it's informational, not the
enforcement mechanism), so the label is already accurate across the backlog
by the time `enforce` mode ships. Label sync is best-effort like the comment;
a sync failure never fails the gate. Skipped on fork PRs; see
[Fork pull requests](#fork-pull-requests).

The label already exists in this repo. If it is ever deleted, the next add
recreates it and retries, so an accidental deletion degrades to a self-heal
instead of a failing sync. The constants in `src/labels/index.ts` therefore
mirror the live label (colour `ededed`) — otherwise a delete-then-heal cycle
would silently reskin it.

The label is a projection for humans (PR-list filtering and search), not a
signal to build on: it is skipped on fork PRs and anyone can add or remove it by
hand. The generator (#57713) must read the ticked opt-out checkbox and the
shared bot-exemption sets in `src/`, never this label.

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

|      File       |                                          Role                                           |
|-----------------|-----------------------------------------------------------------------------------------|
| `src/types.ts`  | The `ParsedRef → ResolvedRef → PolicyDecision` contract + `Resolver` interface          |
| `src/parser/`   | Pure section-scoped reference extraction (shared with the generator)                    |
| `src/resolver/` | GitHub-API adapter (issue vs PR vs missing, cross-repo, backport-hop PR fetch)          |
| `src/policy/`   | Pure PASS/FAIL decision from resolved refs + opt-out state                              |
| `src/title/`    | Pure title lint (commitlint active rules) + bot-author exemption                        |
| `src/gate/`     | Orchestrates link (+ backport hop) + title into one `GateOutcome`                       |
| `src/comment/`  | Sticky-comment render + idempotent upsert (pure logic + `fetch` adapter)                |
| `src/labels/`   | `no-issue` label sync, mirroring the PR-issue-link check (pure logic + `fetch` adapter) |
| `src/gha.ts`    | Minimal `@actions/core` replacement                                                     |
| `src/lint.ts`   | The gate entrypoint (warn-only)                                                         |

## Security model

The gate is one workflow, `release-notes-pr-gate.yml`, on plain **`pull_request`**
(`opened, edited, synchronize, reopened`; base `main` and `stable/**`).
`pull_request_target` is **not** used and cannot be — it is denied repo-wide by
`.github/conftest-gha-best-practices.rego`.

- **Metadata-only.** The action reads the PR through the API and never checks out
  or executes PR code.
- **No privileged token.** `GITHUB_TOKEN` only, with `pull-requests: write` (the
  comment and label) and `issues: write` (recreating a deleted `no-issue` label).
  No Vault, no App token.
- **The check is the job's own conclusion.** GitHub renders it on the PR
  natively, so the gate publishes nothing itself — and it works on fork PRs
  without any token.
- **Accepted trade-off:** on `pull_request` the workflow and this action resolve
  from the PR head, so a PR can edit the code that judges it. That is the same
  trust model as every other lint in `ci.yml` (actionlint, spotless, commitlint),
  and it is acceptable while this check is **advisory** — it is not a required
  status check, so it is not a security boundary. `.github/**` is
  CODEOWNERS-gated, so a tampering diff still needs review.
  ⚠ **This must be resolved before the check is made required**, at which point a
  PR could pass itself by editing the action. See epic #53605.

### Fork pull requests

GitHub gives a fork PR a **read-only** `GITHUB_TOKEN` and withholds secrets, no
matter what `permissions:` asks for. The workflow therefore passes
`can-write: false` for forks (derived from
`github.event.pull_request.head.repo.full_name == github.repository`), and:

|                          Capability                          | Internal PR |  Fork PR  |
|--------------------------------------------------------------|-------------|-----------|
| Parse the body/title, resolve refs, backport hop (API reads) | ✅           | ✅         |
| Job summary + job log                                        | ✅           | ✅         |
| Red/green check row on the PR (the job's conclusion)         | ✅           | ✅         |
| Sticky comment                                               | ✅           | ❌ skipped |
| `no-issue` label                                             | ✅           | ❌ skipped |

So a fork PR is **fully evaluated** and its verdict is fully visible — only the
two writes are skipped, and the job log says so explicitly rather than surfacing
a 403. This is a hard GitHub limitation, not a gap in the action: posting to a
fork PR requires a privileged token, which is precisely what must not be exposed
to PR-head code.

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
