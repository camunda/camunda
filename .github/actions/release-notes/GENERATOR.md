# release-notes generator

The `generate` entrypoint of this action builds a release's changelog from **the pull requests that
actually shipped in it**, grouped under the issues they delivered.

It replaces `zcl`, which built the changelog by labelling issues and then listing everything carrying
the label. That model answers "which issues were closed around this time"; this one answers "what
shipped in this range". The difference matters most where the two disagree, and
[Divergences from zcl](#divergences-from-zcl) is where those are enumerated.

**Read this first if:** a release job failed, a release's notes are missing something, or a release's
notes contain something that should not be there. Jump to
[Troubleshooting](#troubleshooting-by-symptom) — every error string the action can emit is listed
there with the file that produced it.

The PR-gate (`lint` entrypoint) is a different thing living in the same package; see
[README.md](README.md). They share the reference parser and the resolver, nothing else.

---

## Contents

- [What it produces](#what-it-produces)
- [How it runs in the release workflow](#how-it-runs-in-the-release-workflow)
- [Reading a run's log](#reading-a-runs-log)
- [The pipeline](#the-pipeline)
  - [1. Baseline and range](#1-baseline-and-range)
  - [2. Commits to pull requests](#2-commits-to-pull-requests)
  - [3. Attribution — which issue does this pull request deliver?](#3-attribution--which-issue-does-this-pull-request-deliver)
  - [4. Categorization — which section does it go in?](#4-categorization--which-section-does-it-go-in)
  - [5. The delivery claim — released, or partially delivered?](#5-the-delivery-claim--released-or-partially-delivered)
  - [6. Render](#6-render)
- [Edge cases](#edge-cases)
- [Troubleshooting by symptom](#troubleshooting-by-symptom)
- [Divergences from zcl](#divergences-from-zcl)
- [Known limits](#known-limits)
- [Running it locally](#running-it-locally)
- [File map](#file-map)

---

## What it produces

Inputs and outputs are declared in [`generate/action.yml`](generate/action.yml). The action is
**read-only**: it writes files and one step output, and never labels an issue, comments on one, or
touches the release. Publishing is the separate cutover work unit (#57714).

| Written to `output-dir`  |                                                                                                                                                                                                                         Contents                                                                                                                                                                                                                         |
|--------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `CHANGELOG-<version>.md` | The full asset. Every shipped pull request, including the internal-only `Maintenance` section and the unattributed bucket. This is the file attached to the GitHub release.                                                                                                                                                                                                                                                                              |
| `changelog.json`         | One record per pull request: number, title, section, visibility, component, breaking, `issueNumbers`, `closesIssueNumbers`, `attributionSource`. The machine-readable form of the same run.                                                                                                                                                                                                                                                              |
| `labels.json`            | Flat lists of every issue number and pull request number in the release — what the cutover work unit will label.                                                                                                                                                                                                                                                                                                                                         |
| `audit.json`             | Two lists. `overrides`: the `allow-unattributed` exceptions actually applied, one row per pull request with its reason — empty when nothing was overridden, and empty when the guard FAILED, since a failed run overrode nothing. `warnings`: every audit line the run produced, in walk order — range anomalies, ruleset bypasses, truncated fields, attribution and categorization reasons, post-gate anomalies. Always present, empty on a clean run. |
| `comments.json`          | **One entry per issue**, naming every pull request that delivered it, with the comment text and a stable marker so a re-run updates rather than duplicates.                                                                                                                                                                                                                                                                                              |

|   Step output   |                                                               Contents                                                               |
|-----------------|--------------------------------------------------------------------------------------------------------------------------------------|
| `customer-body` | The curated body: customer-visible sections only, unattributed bucket excluded. This is what goes in the GitHub release description. |

Both bodies are also written to the job's step summary, so a reviewer can see the customer body and
the full asset side by side without downloading anything.

Every JSON output carries `schemaVersion` (currently `2.0.0`), so a consumer cannot silently misread
a shape it was not built for. The `2.0.0` bump is `comments.json` moving from one row per
(issue, pull request) pair to one row per issue: the marker is keyed on the issue, so the old shape
emitted several rows sharing one marker and publishing them would have left only the last — 380
comments on 8.9.0, 123 on 8.10.0-alpha5.

---

## How it runs in the release workflow

Today it runs in **shadow mode**: beside the legacy `zcl` changelog, never instead of it. The job is
`release-notes-shadow` in `.github/workflows/camunda-platform-release.yml`.

**It cannot break a release.** Four independent reasons, so that no single one has to hold:

- **Nothing depends on it.** It appears in no other job's `needs`, and neither Slack notification
  job looks at its result.
- **It cannot write.** Its own job, with only `contents: read`, `issues: read`, `pull-requests:
  read`. Token permissions are job-scoped, so this is enforced at the boundary, not just promised by
  the code.
- **Failure is absorbed twice.** `continue-on-error` on the step keeps a failing generator from
  failing the job; `continue-on-error` on the job keeps a failing job — dead checkout, hit timeout —
  from turning the release run red.
- **It touches nothing shared.** Separate job, separate runner, separate filesystem. It writes only
  into its own workspace, never near `changelog.md` or `release-artifacts/`.

**It runs after the `github` job, not beside it.** Both call the API with the same `GITHUB_TOKEN`,
and GitHub's secondary rate limit fires on *concurrency* rather than volume, so running in parallel
could throttle zcl's calls and fail the real changelog. Serialising costs only wall-clock time on a
job nothing waits for.

**Kill switch.** Set the repository variable **`RELEASE_NOTES_SHADOW_DISABLED`** to `true` to turn
the job off — no code change, no merge, effective on the next release. Unset means enabled, so
nothing has to be configured for it to work in the first place.

**Scope: patches and alphas, for now.** Minors are skipped — a minor walks thousands of commits and
predates the PR-issue gate, making it both the slowest run and the noisiest comparison. Candidates
(`-rcN`) are skipped too: both tools chain `rcN` to `rc(N-1)`, so the interesting divergence is in
the releases themselves. Widening the scope means relaxing the version test in the job's `if:`.

**Both dry-run flags are skipped**, for the same underlying reason — the tag the generator is asked
to walk to does not exist in this job's fresh checkout:

- `dryRun` sets `-DremoteTagging=false`, so the tag is created only in the release job's own
  workspace and never pushed.
- `releaseProcessDryRun` *does* push a tag, but names it `dryrun-<version>` while `releaseVersion`
  stays plain. The plain tag is therefore still absent — or, worse, resolves to the **previous real
  release** of that version, and the run silently reports the wrong range.

Giving dry runs real coverage needs a separate input naming a ref that actually exists, which is a
change to what the action accepts rather than a condition tweak.

---

## Reading a run's log

The run emits a fixed sequence of `info` lines. Knowing them makes a failure locatable before you
read any code.

```
Range <baseline-sha-or-tag>..<target>: <N> first-parent commits.
Mapped <N> commits from their own subject; <M> need the commit-to-PR query.
Pre-classified <N> distinct references in <M> requests.
Read labels and the close event of <N> of <M> referenced issue(s).
Generated release notes for <version>: <N> attributed PR(s).
```

|                    Line                     |                                                                                                                                                      What it tells you                                                                                                                                                      |
|---------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Range ...`                                 | The baseline was resolved and git walked it. A wrong range is visible **here** — check it before reading anything else.                                                                                                                                                                                                     |
| `Mapped ... from their own subject`         | How many commits carried their pull request number in the merge subject (the fast path) versus how many needed the expensive `associatedPullRequests` query. On a healthy squash-merge repo the second number is small: 32 of 3,694 on 8.9.0.                                                                               |
| `Pre-classified ...`                        | Every same-repo reference in every pull request body, classified in one batched pass.                                                                                                                                                                                                                                       |
| `Read labels and the close event of N of M` | `M` is every issue attribution produced; `N` how many resolved. `M - N` are references to deleted or transferred issues, which is normal. Every attributed issue is asked about, including ones a backport hop already settled — the delivery rule does not need those, but the `kind/*` visibility rule needs all of them. |
| `Generated release notes ...`               | Success. Files are on disk.                                                                                                                                                                                                                                                                                                 |

Everything else is a `::warning::`. Warnings are **collected and emitted in walk order**, not as
they occur, so the audit log reads the same on every run of the same release. They are also flushed
if the run dies partway — a failed run's diagnostics are the ones most worth reading.

---

## The pipeline

```
|          git            |        GraphQL        |     GraphQL     |  REST + GraphQL
|                         |                       |                 |
| 1. baseline +           | 2. commits to pull    | PR metadata     | 3. attribution
|    first-parent walk    |    requests           |                 | 4. categorization
'-------------------------'-----------------------'-----------------'--------+---------
                                                                              |
   outputs  <---  6. render  <---  5. delivery claim (issue close events)  <---'
```

Each numbered stage is a module with its own tests, and the pure ones (`range`, `attribution`,
`categorize`, `delivery`, `render`) take facts as arguments and do no IO at all, so every branch in
them is reachable from a unit test without a network.

### 1. Baseline and range

**Files:** [`src/range/index.ts`](src/range/index.ts) (pure), [`src/range/walk.ts`](src/range/walk.ts) (the only place that shells out to git).

The baseline is computed **from the version string alone** — no tag list is consulted, every case is
arithmetic on the version number.

|              Target shape               |                          Baseline                           | Resolved by |
|-----------------------------------------|-------------------------------------------------------------|-------------|
| `X.Y.Z` where `Z > 0` (patch)           | tag `X.Y.(Z-1)`                                             | direct      |
| `X.Y.0` (minor)                         | `git merge-base X.Y.0 X.(Y-1).0`                            | fork point  |
| `X.Y.0-alpha1` (first alpha of a cycle) | `git merge-base <target> origin/stable/X.(Y-1)`             | fork point  |
| `X.Y.0-alphaN` where `N > 1`            | tag `X.Y.0-alpha(N-1)`                                      | direct      |
| `<version>-rcM` where `M > 1`           | tag `<version>-rc(M-1)`                                     | direct      |
| `<version>-rc1`                         | whatever `<version>` itself resolves to, per the rows above | see below   |

**Candidates chain.** `8.9.0-rc4` is diffed against `8.9.0-rc3`, `8.10.0-alpha5-rc2` against
`8.10.0-alpha5-rc1`. Each candidate is cut as its own GitHub release and carries the delta since the
one before it; the final untagged version then resolves normally and carries the whole release.
Resolving every candidate against the underlying version instead would republish rc1's entire
changelog under rc2, rc3 and rc4.

The chain terminates at **`rc1`, which has no previous candidate** and so falls through to the
version it stands for: `8.9.0-rc1` resolves `8.9.0`'s baseline, `8.10.0-alpha3-rc1` resolves
`8.10.0-alpha3`'s. Chaining happens **within one alpha**, never across them —
`8.10.0-alpha3-rc2 → 8.10.0-alpha3-rc1`, never back to alpha2's last candidate.

The version a candidate stands for is validated first, so `8.10.1-alpha1-rc2` is rejected for the
same reason `8.10.1-alpha1` is. Only the baseline is computed from the stripped string — the walk,
the labelling and the output filename all keep the real `-rcN` tag.

The walk is `git log <baseline>..<target> --first-parent`. First-parent only: the merge commit of a
pull request is on the line, the commits inside its branch are not, so each pull request appears
exactly once regardless of how many commits it contained.

Rejected at this stage, each with a named error rather than a wrong range:

- `-alpha0` — alphas are 1-based; an alpha0 would compute a baseline of `-alpha-1`, a ref that cannot exist.
- an alpha with a non-zero patch (`8.10.1-alpha1`) — an alpha is a pre-release of a minor, so it
  always carries patch 0. Without this guard it resolved to `8.10.1`, the target's own base version,
  which **can be a real tag** — so the run would have succeeded over a silently wrong range.
- minor `0` (`9.0.0`, `9.0.0-alpha1`) — the baseline for the first minor of a major is the previous
  major's last minor, which no arithmetic on the version string can name. Deliberately rejected until
  a 9.0 release is actually planned.
- dotted alphas (`8.8.0-alpha4.1`), uppercase `-RC1`, bare `-rc`, `-optimize`, `8.10.99-test` — shapes
  the release process no longer cuts. Closed, not oversights; do not widen the regex on the strength
  of old tags.

### 2. Commits to pull requests

**Files:** [`src/range/index.ts`](src/range/index.ts) (`resolveCommitsToPrs`), [`src/resolve/index.ts`](src/resolve/index.ts) (GraphQL).

GitHub writes the pull request number into the subject of the commit it squashes (`... (#61728)`), so
for nearly every commit the mapping is already in hand. That candidate is **derived, never trusted**:
it is confirmed against the pull request's own `mergeCommit`, which must *be* this commit. It is also
looked up *tolerantly* — the number in a subject can be an issue, or typed by hand, and a strict
lookup would abort the whole release on one such commit instead of letting it fall through. Anything
unconfirmed — no number in the subject, unknown pull request, no merge commit, or a merge commit that
is some other commit — falls back to `associatedPullRequests` (filtered to `MERGED`, paginated), so a
wrong guess cannot become a wrong attribution.

Four rules then decide what ships:

1. **A pull request ships only if its own merge commit is one of the walked commits.** Commits pushed
   straight onto a release branch — the release plugin's version bumps, and the reverts that repair an
   orphaned tag — have no pull request of their own, so GitHub credits them to whichever pull request
   later swept that branch into stable. That is the *next* release's merge-back. Without this check,
   8.9.19's notes listed a pull request merged three days after the tag was cut.
2. **Release merge-backs are excluded.** A pull request whose head branch is `release-<version>` and
   whose base is `stable/*` or `main` merges a release branch back into the line it was cut from; it
   delivers nothing of its own. The pattern is anchored on the version (`^release-\d+\.\d+\.\d+`) —
   a bare `release-` prefix also matched a feature branch called `release-notes-gate`, which would
   have dropped real delivered work.
3. **Commits with no pull request at all** are a *ruleset-bypass anomaly* and are warned about, unless
   the message matches the automation whitelist: `[maven-release-plugin]` and
   `Revert "[maven-release-plugin]`. A release job that redoes its version bumps reverts them first,
   so those reverts are as much release automation as the commits they undo.
4. **Ambiguity is never guessed.** A commit associated with several shipped pull requests prefers the
   one targeting the release **line**; still tied, it is warned about and skipped. The line is not
   the `release-branch` input as given: the release workflow passes its temporary `release-X.Y.Z`
   branch, which nothing ever merges into, so a literal match would never pick a winner and every
   ambiguous commit would be skipped. A version-shaped `release-X.Y.Z` is normalized to
   `stable/X.Y` **or** `main` — a patch and a post-branch alpha ship from the first, a pre-branch
   alpha from the second, and accepting both only narrows an ambiguity that would otherwise be
   abandoned. Any other value is matched literally.

A `MERGED` pull request that GitHub reports with **no merge commit** is a data anomaly. It is **kept**,
with a warning saying its range membership is unverified — under-inclusion is the failure this work
exists to fix.

These three exclusion outcomes are deliberately reported as three different sentences, not one. A
merge-back is excluded *even though* it merged here; an out-of-range pull request is excluded
*precisely because* it did not; a commit with no pull request is a possible ruleset bypass. Collapsing
them produces an audit line that is flatly untrue.

### 3. Attribution — which issue does this pull request deliver?

**Files:** [`src/attribution/index.ts`](src/attribution/index.ts) (pure chain), [`src/pipeline/index.ts`](src/pipeline/index.ts) (composition, backport hop), [`src/parser/index.ts`](src/parser/index.ts) (reference parsing, shared with the gate).

The chain runs **unconditionally in this order** and terminates at the first step that decides:

| Step |    `attributionSource`    |                                                                                             Meaning                                                                                             |
|------|---------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1    | `optOut`                  | The `Related issues` opt-out checkbox is ticked — an author's deliberate declaration. No issues.                                                                                                |
| 2    | `section`                 | Eligible references in the `## Related issues` section resolve to at least one live issue.                                                                                                      |
| 2′   | `resolutionFailed`        | The section *had* eligible references but **none** resolved to a live issue. A real problem: the link exists, its target is gone.                                                               |
| 3    | `closingIssuesReferences` | GitHub's own native closing-reference field is non-empty.                                                                                                                                       |
| 4    | `legacyBodyScan`          | A reference found anywhere in the body, outside the section.                                                                                                                                    |
| 5    | `unattributed`            | Nothing found anywhere. A real problem: no link at all.                                                                                                                                         |
| —    | `botExempt`               | Applied **last**, only over `unattributed`/`resolutionFailed`, for authors in `BOT_LINK_EXEMPT` (currently `renovate[bot]`). An exempt bot that *did* link a real issue keeps that attribution. |

Parsing rules that catch people out:

- **HTML comments are stripped before anything is parsed.** The PR template's own instructional
  `<!-- ... closes #1234 ... -->` block lives inside `## Related issues` and is invisible in the
  rendered body, so a pull request that left the boilerplate untouched must not be attributed to
  whatever issue the comment names.
- **Cross-repo references never attribute.** `camunda/other#7` is recorded and reported, never used.
- **A `Backport of #N` marker is a delivery-hop signal, not an attribution reference.**
- At most **20** references per pull request are resolved (`MAX_REFS`), prioritised so closing
  references survive the cap ahead of merely informational ones, and at most **5** in flight
  (`CONCURRENCY`). This is a defence against a body engineered to fan out into hundreds of API calls.
- The legacy body-wide scan only runs when the earlier steps cannot terminate. Since the body
  *contains* the section, running it unconditionally resolved every section reference a second time.

**The backport hop.** If a backport marker is present, the decision is taken from **the original pull
request's body**, not the backport's. The hop fires whenever the marker is present, not only when the
backport's own body yielded nothing: `backport-action` copies the original's references into
`relates to ...` *without stripping HTML comments*, so the template's own example references arrive as
visible, author-looking references. A body-wide scan of that *succeeds* — which is exactly why gating
the hop on failure once let a 2018 issue title describe a 2026 fix. An explicit opt-out tick on the
backport is the one thing that outranks the original: unlike a copied reference, it is a deliberate
statement about this pull request.

**The post-gate anomaly.** When `gate-required-at` is set, a pull request merged after that watermark
should have terminated at the section step by construction. Reaching `closingIssuesReferences` or
`legacyBodyScan` instead means the section contract was not observed, and the run warns
`post_gate_fallback_attribution`. For a backport hop the timestamp compared is **the original's** — a
post-gate backport of a pre-gate original is not a gate violation. This affects the warning only,
never the attribution.

### 4. Categorization — which section does it go in?

**File:** [`src/categorize/index.ts`](src/categorize/index.ts) (pure).

The conventional-commit type in the title maps to a section:

|                     Type                      |            Section             |           Visibility           |
|-----------------------------------------------|--------------------------------|--------------------------------|
| `feat`                                        | Features                       | customer                       |
| `fix`                                         | Bug Fixes                      | customer                       |
| `perf`                                        | Performance                    | customer                       |
| `docs`                                        | Documentation                  | customer                       |
| `deps`                                        | Dependency updates             | customer                       |
| `revert`                                      | Reverts                        | customer                       |
| `refactor`, `build`, `ci`, `test`, `style`    | Maintenance                    | **internal** — full asset only |
| `merge`                                       | *(excluded from both outputs)* | —                              |
| anything else, or a title that does not parse | Uncategorized                  | customer                       |

An unparseable title is **never dropped** — it lands in `Uncategorized` and produces a warning naming
the title and its author. On a pre-gate range this is loud by design: 8.9.0 produced 665 uncategorized
entries from historical non-conventional titles.

Bot titles that cannot be trusted as the category source are overridden by author:

|                        Author                        |                                         Override                                          |
|------------------------------------------------------|-------------------------------------------------------------------------------------------|
| `backport-action`, `monorepo-devops-automation[bot]` | `inherit-original` — the original pull request's title is substituted before categorizing |
| `renovate[bot]`, `dependabot[bot]`                   | forced to `deps`                                                                          |

An unlisted bot is treated like a human: its title must parse. This is deliberate — `qa-processes[bot]`,
for example, titles everything `test:`/`ci:` already, so a map entry would add nothing but a place to
drift.

For a `deps:` pull request the line is rewritten as `name: old → new`, read from dependabot's title or
from renovate's body table. If neither shape matches, the plain title is kept.

Component comes from component labels: none → `null`, one → that label, several → `Multiple components`
plus a warning naming them. `breaking` comes from the breaking-change label.

**The issue's `kind/*` label overrides visibility.** A section describes the *change*; it says nothing
about who the change is for. A CI gate lands as `feat:`, a flaky-test repair as `fix:`, a load-test
folder marker as `docs:` — and each then reads to a customer as a feature, a bug fix and a
documentation change. The audience is recorded only on the issue, and only there: a delivering pull
request carries no `kind/*` label of its own. So an entry whose linked issues are **all**
`kind/task` or `kind/epic` is forced to `internal` — kept in the full asset, hidden from the
customer body, never dropped, with a warning naming the label that did it.

Hidden only when *every* linked issue is internal. One pull request routinely closes a customer bug
and a QA task together — 8.9.19's #61857 closed both `kind/bug` #61719 and `kind/task` #56995 — and
hiding on any internal label would suppress the real fix along with the task. A pull request linking
no issue at all is never hidden: absence is not a signal.

Measured on 8.9.19: this removed 3 of 23 customer-facing lines, all internal work — the PR-gate
epic under **Features**, a flaky integration test under **Bug Fixes**, and an *8.10* load-test epic
under **Documentation**.

### 5. The delivery claim — released, or partially delivered?

**File:** [`src/delivery/index.ts`](src/delivery/index.ts) (pure), fed by `fetchIssueClosers` in [`src/resolve/index.ts`](src/resolve/index.ts).

This decides, per issue, whether **this** pull request is the one that delivered it — the difference
between `Released in 8.9.19 (#61871)` and `Partially delivered in 8.9.19 by #61960` in `comments.json`.

The question is asked **of the issue, not of the pull request**. A `closes` keyword is a statement of
intent in a body that can be edited afterwards, and several pull requests delivering one issue each
write one. `ClosedEvent.closer` is what GitHub recorded when the state actually changed: exactly one
pull request can be the closer, and no later body edit moves it.

Three rules, in order:

1. **A backport hop is trusted wholesale.** The backport bot writes no closing keyword, and a merge
   into a stable branch fires no close event at all — GitHub only auto-closes from the **default**
   branch. Both signals are structurally blank for every backport, so trusting the hop is what keeps a
   patch release from reporting its entire contents as partial.
2. **Where GitHub recorded a closer, it decides — both ways.** Naming another pull request is a
   positive statement that this one did *not* close the issue, even if this one's body says it did.
3. **Where it recorded none** — the issue is open, a human clicked Close, or a bare commit closed it —
   fall back to the pull request's declared `closingIssuesReferences`. This is the off-default-branch
   case seen from the other side: a fix merged straight to `stable/8.9` fires no close event, so its
   own keyword is the only signal that exists.

An issue closed as `NOT_PLANNED` or `DUPLICATE` is excluded under every rule but the backport hop:
whatever a body claims, GitHub's own record says that issue was abandoned, not shipped.

**A closer in another repository is not read as one of ours.** `camunda/camunda-docs#4852` genuinely
closes `camunda/camunda#26937`, and its number means nothing in this repository's numbering — read
unguarded it would credit whichever unrelated pull request happens to share it.

The lookup is one batched phase **after** attribution, because the issue set is what attribution
produces. Issues a backport hop already settled are never looked up.

### 6. Render

**File:** [`src/render/index.ts`](src/render/index.ts) (pure — which issues a pull request closed is supplied by the caller, never derived here).

**The unit of presentation is the user-visible change, not the pull request.** Entries are grouped by
the pull request's **first linked issue**; a pull request with no issue (opt-out, bot-exempt,
unattributed) has nothing to group under and stays a single-PR entry.

```
- Add agent history API (#55) — #101, #102, #103, #104
- Fix draining state never written (#61277) — #61874
- Rework the scheduler (#59931) — #61717 (partially delivered)   ← issue still open
- ci: bump the runner image (#60840)          ← no issue: pull request only
```

**An entry whose issue is still open says so.** Grouping puts one line under the
issue's own title, which reads as the whole feature shipping — so an entry whose
issue GitHub still reports as `OPEN` is marked `(partially delivered)`. Keyed on the
issue being open, deliberately *not* on "did a pull request in this range close it":
8.9.19's #55699 is closed as `COMPLETED` with no recorded closer, because a human
clicked Close, and calling that partially delivered would be a false claim about
finished work rather than caution. An issue absent from the lookup is never marked —
absence is not evidence.

**Dependency bumps group by package, not by pull request.** A release that moves one
package five times published five lines a reader had to reconcile by hand, and 8.9.19
shipped two byte-identical `io.github.classgraph: 4.8.193 → 4.8.194` lines from #61527
and #61528. Each package now gets one line spanning where the release started to where
it ended, citing every pull request that moved it:

```
- io.github.classgraph:classgraph: 4.8.193 → 4.8.194 (#61527, #61528)
```

Across pull requests the walk order settles which update came first — newest first, so
the earliest is last. Within one pull request it cannot: a grouped renovate body lists
rows per lockfile, not in time order, and the positional answer gave
`browserslist: 4.28.2` when the release really started at `4.28.1`. So versions are
compared numerically where they can be, and walk order is the fallback for anything
unorderable — a digest or a short sha, where walk order *is* the chronology. The same
collapse fixes a single renovate pull request listing one package twice, and a pull
request bumping several packages now produces one line each instead of a `;`-joined
run-on.

Where a group's pull requests disagree on section — a `feat`, a `fix` and two `refactor`s delivering
one issue — the **most customer-visible** section wins, ranked by the section order below. Deliberately
*not* "the section of the pull request that closed the issue": that would depend on the closer lookup
and has no answer at all when nothing in the range closed the issue.

Section order (also the output order):

```
Features · Bug Fixes · Performance · Documentation · Dependency updates · Reverts ·
Changes without a tracked issue · Maintenance · Uncategorized
```

Breaking changes are additionally hoisted into a `## Breaking changes` section at the top. An opt-out
pull request is grouped under `Changes without a tracked issue`, never under its type's section.

The two bodies differ by audience:

|                        | Customer body (`customer-body` output) | Full asset (`CHANGELOG-<version>.md`) |
|------------------------|----------------------------------------|---------------------------------------|
| `Maintenance`          | excluded                               | included                              |
| unattributed bucket    | excluded                               | included                              |
| pull request citations | only the ones it may show              | every contributor                     |

**The unattributed guard.** If any pull request landed in the `unattributed`/`resolutionFailed` bucket,
the job **fails by default**. Every output is still written first — `audit.json`'s whole purpose is
explaining which pull requests and why — and only then does the job fail. Override with
`allow-unattributed: true` **and** a non-empty `unattributed-reason`, which is recorded per pull request
in `audit.json`. The failure message names the two kinds separately, because they need opposite fixes:
`unattributed` needs a link added; `resolutionFailed` already has one and its *target* is gone.

---

## Edge cases

A quick index of everything handled specially, and where.

|                           Case                            |                                 Behaviour                                  |                  File                  |
|-----------------------------------------------------------|----------------------------------------------------------------------------|----------------------------------------|
| Release candidate `rcM`, `M > 1`                          | diffed against `rc(M-1)`, within the same alpha                            | `range/index.ts`                       |
| Release candidate `rc1`                                   | falls through to the version it is a candidate for                         | `range/index.ts`                       |
| `-alpha0`                                                 | rejected as unrecognized                                                   | `range/index.ts`                       |
| Alpha with non-zero patch                                 | rejected with a named error                                                | `range/index.ts`                       |
| First minor of a major (`9.0.0`)                          | rejected — baseline underivable                                            | `range/index.ts`                       |
| Dotted alpha (`8.8.0-alpha4.1`)                           | rejected; closed shape, not a parser gap                                   | `range/index.ts`                       |
| Release merge-back                                        | excluded by branch topology                                                | `range/index.ts`                       |
| Version-bump / orphaned-tag-repair commits                | whitelisted, not reported as bypasses                                      | `range/index.ts`                       |
| Commit with no pull request                               | ruleset-bypass warning                                                     | `range/index.ts`                       |
| Commit with several pull requests                         | prefers the one targeting the release branch; else warns and skips         | `range/index.ts`                       |
| `MERGED` pull request with no merge commit                | kept, membership stated as unverified                                      | `range/index.ts`                       |
| Rebase-merged pull request (no `(#N)` in subject)         | falls back to the `associatedPullRequests` query — about 19 per minor      | `generate.ts`                          |
| PR template boilerplate in an untouched body              | HTML comments stripped before parsing                                      | `parser/index.ts`                      |
| Cross-repo reference                                      | recorded, never attributes                                                 | `attribution/index.ts`                 |
| Dead / deleted issue reference                            | `resolutionFailed`, named in the failure message                           | `attribution/index.ts`                 |
| Backport pull request                                     | attributed from the **original's** body                                    | `pipeline/index.ts`                    |
| Opt-out ticked on a backport                              | outranks the original                                                      | `pipeline/index.ts`                    |
| `renovate[bot]` with no link                              | `botExempt`, does not fail the guard                                       | `pipeline/index.ts`, `title/index.ts`  |
| Post-gate fallback attribution                            | warning only, never changes attribution                                    | `attribution/index.ts`                 |
| Unparseable title                                         | `Uncategorized` + warning; never dropped                                   | `categorize/index.ts`                  |
| `merge:` title                                            | excluded from both outputs, and never trips the guard                      | `categorize/index.ts`, `generate.ts`   |
| Several component labels                                  | `Multiple components` + warning                                            | `categorize/index.ts`                  |
| >20 labels or >20 native closing refs on one pull request | truncation warned per field                                                | `resolve/index.ts`                     |
| Issue closed by a human or a bare commit                  | falls back to the declared keyword                                         | `delivery/index.ts`                    |
| Issue closed as `NOT_PLANNED` / `DUPLICATE`               | never reported as delivered                                                | `delivery/index.ts`                    |
| Issue closed from another repository                      | not read as one of ours                                                    | `resolve/index.ts`                     |
| Several pull requests delivering one issue                | one rendered line citing all of them; only the real closer says "Released" | `render/index.ts`, `delivery/index.ts` |
| Nonexistent issue number in a batch                       | tolerated per-alias; the batch survives                                    | `resolve/index.ts`                     |

---

## Troubleshooting by symptom

### The job failed

|                                               Message                                               |                                                             Cause                                                              |                                                                                            What to do                                                                                            |
|-----------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Not a recognized release version: "X"`                                                             | The version does not match `X.Y.Z[-alphaN][-rcM]`.                                                                             | Check the tag. If it is a shape we genuinely cut now, widen `VERSION` in `src/range/index.ts` — and add the case to [Edge cases](#edge-cases).                                                   |
| `Unsupported release version "X": an alpha is a pre-release of a minor, so it must carry patch 0.`  | Something tagged `X.Y.Z-alphaN` with `Z > 0`.                                                                                  | The tag is wrong, not the action. This guard exists because the wrong baseline here *succeeds* silently.                                                                                         |
| `...the baseline for the first minor of a major...cannot be derived from the version number alone.` | A `X.0.0` release.                                                                                                             | Deliberately unsupported. Decide the baseline by hand and extend `resolveBaselineStrategy`.                                                                                                      |
| `Release-notes attribution gate failed for N pull request(s).`                                      | Pull requests with no linked issue, or whose every linked issue is dead.                                                       | The message names both groups separately. Fix the links, or re-run with `allow-unattributed: true` and a reason. All outputs — including `audit.json` — were already written before the failure. |
| `GITHUB_REPOSITORY must be set to "owner/repo", got ""`                                             | Running outside Actions without the variable.                                                                                  | See [Running it locally](#running-it-locally).                                                                                                                                                   |
| `gate-required-at must be a parseable date, got "X"`                                                | Malformed workflow input.                                                                                                      | ISO-8601, or empty.                                                                                                                                                                              |
| `GitHub GraphQL API returned HTTP 401/404`                                                          | Token missing or lacking read scope.                                                                                           | The action needs read-only access to commits, pull requests and issues.                                                                                                                          |
| `GitHub API kept returning HTTP 403 past 5 attempts`                                                | GitHub's **secondary** rate limit, which fires on concurrency rather than volume — the primary quota can still read 5000/5000. | Re-run. If it repeats, lower `WORKERS` in `src/generate.ts` (currently 3); `resolveRefs` already runs up to `CONCURRENCY` per pull request, so the two limits multiply.                          |
| `GitHub GraphQL request kept failing (...) past 5 attempts`                                         | A batch that stayed broken. Commit batches bisect down to a single commit before giving up; a failure at that size is real.    | Read the named cause. A `502` on the commit query is GitHub timing out on query cost — lower `COMMIT_BATCH_SIZE`.                                                                                |
| `Malformed GraphQL response: missing <field>`                                                       | GitHub returned a shape the client did not expect.                                                                             | Not transient — the same request fails identically at any size. The message names the exact field and the commit or pull request it belonged to.                                                 |
| `GitHub GraphQL error: <message>`                                                                   | A non-`NOT_FOUND` field error, e.g. `FORBIDDEN`.                                                                               | Deliberately fatal. Only missing-node errors are tolerated.                                                                                                                                      |

### The job passed, but the notes look wrong

|                         Symptom                         |                                                  Likely cause                                                  |                                                Where to look                                                 |
|---------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| Far too many or too few entries                         | Wrong baseline.                                                                                                | The first log line. Compare against [the baseline table](#1-baseline-and-range).                             |
| A pull request from *after* the tag appears             | Should be impossible — the merge-commit membership check exists for exactly this.                              | `resolveCommitsToPrs` in `src/range/index.ts`; check the warning list for `did not merge inside this range`. |
| The previous release's merge-back appears               | The head branch did not match `^release-\d+\.\d+\.\d+`.                                                        | `isReleaseMergeBack` in `src/range/index.ts`.                                                                |
| A delivered change is missing entirely                  | Its commit was warned about — ambiguous, or credited only to out-of-range pull requests.                       | Search the warnings for its commit SHA.                                                                      |
| Everything is in `Uncategorized`                        | Titles predate conventional commits. Expected on old ranges.                                                   | Warning count; `categorize/index.ts`.                                                                        |
| A backport's line shows the wrong issue title           | The hop inherited the original — verify the `Backport of #N` marker points where you think.                    | `pipeline/index.ts`.                                                                                         |
| An issue says "Released" but was not finished           | The closer lookup found no recorded closer and fell back to the declared keyword.                              | `delivery/index.ts` rule 3; check the issue's own close event.                                               |
| An issue that *was* finished says "Partially delivered" | GitHub records a different pull request as the closer — often the original on `main` for work backported here. | `delivery/index.ts` rule 2.                                                                                  |
| One issue produces several near-identical lines         | Its pull requests linked *different* first issues, so they grouped apart.                                      | `entryKeyFor` in `src/render/index.ts`.                                                                      |

### CI on a pull request touching this package

|                                                                    Symptom                                                                     |                                                                                                                                                     Cause                                                                                                                                                     |
|------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Lint / Release-notes action / verify` fails at `Fail if committed dist is stale`, but `npm ci && npm run build` reproduces the bundle locally | The branch is behind `main` and `main` bumped the bundler. CI builds the **merge** of the branch into main, pairing main's `@vercel/ncc` with your branch's bundle. Rebase onto `main`, re-run `npm ci`, rebuild, commit the bundle. A locally reproducible build proves nothing until the branch is current. |

---

## Divergences from zcl

Both tools resolve the **same range endpoints** — verified by running zcl's own
`camunda/infra-global-github-actions/previous-version` resolver against this repository's tags:
`8.10.0-alpha5 → 8.10.0-alpha4`, `8.9.19 → 8.9.18`, `8.9.0 → 8.8.0` (and `git merge-base 8.8.0 8.9.0`
is exactly the fork point this action uses, so those two are the same commit set).

What differs is everything after that:

|            |                                       zcl                                        |                            this generator                            |
|------------|----------------------------------------------------------------------------------|----------------------------------------------------------------------|
| Unit       | the issue                                                                        | the pull request, grouped under its issue                            |
| Selection  | issues carrying a `version:<tag>` label, applied by a separate `add-labels` pass | pull requests whose merge commit is on the range's first-parent line |
| Traversal  | all parents (14,529 commits on 8.9.0)                                            | first-parent only (3,694)                                            |
| Candidates | `rcN` walks back to `rc(N-1)`                                                    | same — `rcN` walks back to `rc(N-1)`, `rc1` to the version itself    |

Consequences, measured against the shipped assets for four releases:

- **Issues zcl publishes that this does not**: 0 of 10 on 8.9.19, 1 of 237 on 8.10.0-alpha5, 4 of 896
  on 8.9.0. Every one of them was either closed with *no pull request delivering it* — which an
  issue-label scrape publishes and a PR-as-unit generator by definition does not — or closed by a pull
  request that is not on the first-parent line.
- **Entries this publishes that zcl does not**: 204 extra issues on the alpha, 846 on the minor, all
  from pull requests whose range membership is verified against git.
- **Candidate releases agree**: both chain `rcN` to `rc(N-1)`, verified by running zcl's own resolver
  (`8.10.0-alpha5-rc2 → 8.10.0-alpha5-rc1`, `8.9.0-rc2 → 8.9.0-rc1`). Over the real
  `8.10.0-alpha5-rc1..rc2` range (32 commits) the generator carries 4 of zcl's 4 issues and names no
  pull request off the first-parent line.

---

## Known limits

Recorded so they are not mistaken for bugs, and not re-investigated.

- **The delivery claim is a point-in-time snapshot.** It is computed at tag time and published
  immutably. An issue edited, reopened or re-closed afterwards desyncs from the notes. Accepted:
  release notes describe what shipped, and shipping is immutable.
- **The backport-hop exception is structural, not a shortcut.** GitHub only auto-closes issues from
  the default branch, so on every stable-branch release the recorded closer is the original `main`
  pull request, permanently out of range. The closer lookup cannot remove this exception.
- **A pre-gate range needs `allow-unattributed`.** 8.9.0 carries 1,005 pull requests with no linked
  issue, because the PR-gate did not exist yet. This is by design: the guard fails by default and the
  operator states a reason.
- **Major releases are rejected**, pending a real 9.0 plan.
- **Cross-repo references never attribute**, even when they are the only link a pull request has.

---

## Running it locally

Inputs arrive as `INPUT_*` environment variables, and the working directory must be a checkout with
the release tags and the relevant `origin/stable/*` refs present.

```bash
cd <a camunda/camunda checkout>
mkdir -p /tmp/out
env \
  GITHUB_REPOSITORY=camunda/camunda \
  "INPUT_TOKEN=$(gh auth token)" \
  "INPUT_TARGET-VERSION=8.9.19" \
  "INPUT_RELEASE-BRANCH=stable/8.9" \
  INPUT_ALLOW-UNATTRIBUTED=true \
  "INPUT_UNATTRIBUTED-REASON=local validation run" \
  "INPUT_OUTPUT-DIR=/tmp/out" \
  "GITHUB_OUTPUT=/tmp/out/gha-output.txt" \
  "GITHUB_STEP_SUMMARY=/tmp/out/summary.html" \
  node .github/actions/release-notes/generate/dist/index.js
```

Note the dashes: `INPUT_TARGET-VERSION` is not a valid shell identifier, so it must be set through
`env` rather than the `VAR=value command` prefix form.

Expect roughly 5 minutes for a 3,694-commit minor, seconds for a patch. A minor is the only shape that
reliably exercises the rate-limit and batching paths, so validate against one before trusting a change
to `src/resolve/`.

Build and test:

```bash
cd .github/actions/release-notes
npm ci
npm run typecheck     # tsc --noEmit
npm test              # node --test over test/*.test.ts
npm run build         # rebuilds BOTH lint/dist and generate/dist — commit the result
```

The bundles under `lint/dist/` and `generate/dist/` are committed and CI fails if they drift from a
fresh build.

---

## File map

```
src/
  generate.ts        entrypoint: wires every stage, owns the worker pool and batching
  range/index.ts     baseline strategy, commit→PR dedupe, merge-back and range-membership rules
  range/walk.ts      the only git calls: merge-base and the first-parent log
  resolve/index.ts   batched GraphQL: commit→PR, PR metadata, ref classification, issue close events
  resolve/warm.ts    serves pre-classified refs to the per-PR resolver without a second round trip
  resolver/index.ts  per-ref REST resolution + the MAX_REFS priority policy (shared with the gate)
  parser/index.ts    section extraction, opt-out detection, reference parsing (shared with the gate)
  attribution/       the unconditional attribution chain and the post-gate anomaly rule
  pipeline/index.ts  one pull request end to end: attribution + backport hop + categorization
  categorize/        title type → section, bot overrides, dependency-update formatting
  delivery/index.ts  released vs partially delivered, from the issue's own close event
  render/index.ts    grouping, both bodies, the four JSON outputs, the unattributed guard
  title/index.ts     title lint and the bot link exemption (shared with the gate)
  types.ts           the reference/resolution types both entrypoints share
  gha.ts             the ~7 Actions toolkit calls this package uses, inlined
  lint.ts            the OTHER entrypoint — the PR-gate. Not part of this flow;
                     documented in README.md

test/                one file per module; the pure modules need no network at all
generate/action.yml  the generator's inputs and outputs
generate/dist/       committed ncc bundle — rebuild with `npm run build`
```

**Security notes.** Every identifier — owner, repo, SHA, pull request number, cursor — travels as a
GraphQL *variable*, never concatenated into a query document. Git is invoked through `execFileSync`
with an argv array and no shell, so a ref can never be read as a command. Nothing logs the token,
request headers, or a raw response. Anything interpolated into the step summary is HTML-escaped,
because titles and bodies are user-controlled.
