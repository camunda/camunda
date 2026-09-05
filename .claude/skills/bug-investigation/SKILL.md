---
name: bug-investigation
description: Autonomously investigate a GitHub issue in camunda/camunda — read it, find the relevant code, reproduce it, root-cause it, and validate a fix, posting progress as a single updating issue comment. At Medium/High confidence, implements the fix and opens it as a draft PR. Use when asked to investigate, triage-deep-dive, or look into a specific bug issue (given a URL or issue number).
---

# Bug Investigation Agent

Investigates a single already-triaged bug issue end to end, reports back on the issue itself, and —
when confidence is Medium or High — implements and opens the fix as a **draft** PR.
See [Hard rules](#hard-rules).

## When to use

Engineer says something like:

> "Investigate this issue: https://github.com/camunda/camunda/issues/XXXXX"

No issue reference → ask for one; don't infer from recent conversation context. Non-`camunda/camunda`
repo → confirm with the engineer before proceeding; some reproduction steps (c8run, the e2e suite)
assume monorepo conventions.

This complements the existing automated triage (severity/likelihood/urgency labeling). This skill
starts **after** an issue already has those labels — it does the investigation work a human
engineer would otherwise do once a bug is picked up. See
[`references/triage-guidelines.md`](references/triage-guidelines.md) for how to read those signals.

## Prerequisites

```bash
gh auth status  # must succeed against the issue's repo
```

CWD is the repo root, on a clean or disposable branch (reproduction may start local processes).

## Hard rules — read first

1. **PRs are opened only at Medium or High confidence, and always as drafts.** At Low confidence,
   this skill only investigates and posts findings as a GitHub comment — it does not implement or
   open anything. At Medium/High confidence it implements the fix, validates it (Phase 5), and opens
   a **draft** PR (`gh pr create --draft`) — never ready-for-review. See
   [Phase 6](#phase-6--score-confidence-and-decide) and [PR creation](#pr-creation) for the exact
   procedure. This default (draft, Medium-or-above) came from explicit engineer instruction — don't
   revert to "report only" or "non-draft" without being told to.
2. **The fix branch contains only the fix.** Create a fresh branch from `origin/main` (not local
   `main`, which may be behind) and commit **only** the files the fix actually touches. Never sweep
   in unrelated local working-tree state (other in-progress edits, this skill's own files, stray
   config) just because they happened to be present when the branch was created — check
   `git status` before staging and stage files by name, never `git add -A`/`git add .`.
3. **Single comment, PATCHed in place.** Post one comment when investigation starts; every phase
   update PATCHes that same comment. Never post a second comment for progress (the PR link goes into
   this same comment via the final PATCH, and — separately, since GitHub doesn't let a comment cross
   into another issue's thread — into the PR description itself). See
   [GitHub comment lifecycle](#github-comment-lifecycle).
4. **Logs are never a blocker.** Investigate with whatever the issue already has (description,
   stack trace, error message, CI links). If logs would move confidence from Medium to High, say so
   as an explicit open question in the report — do not pause and wait for someone to attach them.
5. **Always tear down reproduction environments**, success or failure. A crashed investigation
   leaving a live `c8run` or dangling browser processes is worse than reporting "could not
   reproduce."
6. **Diff size gates the PR decision, not the confidence score.** Confidence ≥ 0.80 with a diff
   estimated ≥ 1000 lines still gets Medium treatment for the PR-decision — large diffs need human
   judgment regardless of how confident the root-cause analysis is. At Medium, still open the draft
   PR, but tag the issue owner/assignee for extra scrutiny before it's marked ready.
7. **Never implement a fix that is itself a breaking change.** This includes: altering a REST/gRPC
   API's request/response shape or status codes, renaming/removing an exported type, field, or CLI
   flag, or changing an on-disk/wire/exported-record format in a way existing callers depend on. This
   mirrors the repo's `AGENTS.md` "ask first" rule for public API contracts — this skill runs
   autonomously and has no one to ask, so it must not cross that line on its own. Phase 5's
   compatibility check (below) is where this gets verified before any PR is opened. If the root cause
   can only be fixed by breaking a public contract, treat it as Low confidence — do not implement
   it — and report the trade-off (fix requires a breaking change vs. a smaller non-breaking
   workaround) so the engineer can decide.
8. **Never open a PR that fixes only some of the storage backends the repo supports.** Camunda ships
   RDBMS, Elasticsearch, and OpenSearch as interchangeable backends behind the `search/` abstraction
   (see the path map in the repo's `AGENTS.md`). A root cause in shared query/aggregation/statistics
   logic almost always has a twin implementation per backend — e.g.
   `search/search-client-query-transformer/` (ES/OS) and `db/rdbms/src/main/resources/mapper/` (RDBMS
   MyBatis mappers). If the bug reproduces on one backend, assume it reproduces on all of them unless
   proven otherwise, and check each one explicitly in Phase 2. Fixing RDBMS while leaving ES/OS (or
   vice versa) with the same wrong behavior is **not** an acceptable partial fix — it just trades one
   bug for a worse one (correct behavior depends on which backend the deployment happens to use).
   This is a harder constraint than Phase 6's general "fine to implement a subset" allowance, which
   is for genuinely independent fixes at different layers (e.g. a frontend workaround alongside a
   backend root-cause fix) — it does not license splitting one root-cause fix across backends. See
   Phase 5's backend-parity check and Phase 6's decision table for how this plays out in practice.
9. **When running autonomously, always post — never ask whether to.** Whether to post progress to
   the GitHub issue is a one-time question asked in Phase 0, and it's only safe to ask when an
   engineer is actually present to answer it. In an autonomous run (e.g. triggered by an automated
   workflow) there is no one to respond — asking anyway would hang the run indefinitely waiting for
   an answer that will never come, the same failure mode as Phase 3's health-check loop without a
   timeout. Autonomous invocations must say so explicitly and skip the question entirely, defaulting
   to posting.
10. **Never substitute a similarly-named file for a verbatim identifier from the issue, and never
    conclude "no fix needed" or "issue premise is wrong" without matching that identifier exactly.**
    When the issue's logs, stack trace, or error message name a fully-qualified class, a log
    format string, or exact error text, that string is ground truth — grep for it verbatim (Phase 2)
    before treating any similarly-named class in a different package/module as "the relevant file."
    If the exact string isn't found on `main`, and the issue names a specific released version,
    check that version's `stable/X.Y` branch (or tag) before concluding the code doesn't exist at
    all — a class removed or renamed on `main` while still shipping on a supported release branch is
    a common, distinct case from "doesn't exist anywhere," and confusing the two produces a
    confident-but-wrong "already fixed, no action needed" conclusion. If Phase 5's fix attempt fails
    with a structural error unrelated to the fix's own logic (e.g. "Ambiguous `@ExceptionHandler`",
    a missing bean, a type that doesn't resolve), treat that as a signal you may be editing the wrong
    file, not as evidence the real app already handles it correctly — re-open Phase 2 before drawing
    any conclusion. This rule exists because a real investigation matched a similarly-named
    controller in the wrong module, reproduced the wrong endpoint, hit exactly this kind of
    structural failure when attempting a fix there, and told the reporter no fix was needed — when
    the actual reported class, never located, was still broken on every supported release branch.

## Procedure

### Phase 0 — Start the comment

Parse `owner/repo` and issue number from the URL/reference given, then check whether this issue is
already closed or already has a linked PR before doing anything else — including before posting
any comment:

```bash
gh issue view <issue_number> --repo <owner>/<repo> --json state,closedByPullRequestsReferences
```

If `state` is `CLOSED`, or `closedByPullRequestsReferences` is non-empty, stop here and tell the
engineer directly — don't post a comment, don't investigate. An "investigation in progress"
comment on an issue someone already closed or is already fixing is exactly the duplicate work this
check exists to prevent.

Otherwise, decide once whether to post to the issue at all, before doing anything else. If an
engineer is present in this session, ask now: "Post progress and the final report on the GitHub
issue, or just report back here?" — default to posting if they don't say. If running autonomously
(no engineer present to ask — see Hard rule 9), skip the question and always post. This decision
holds for the rest of the investigation; see [GitHub comment lifecycle](#github-comment-lifecycle)
for what each phase's "update the comment" step means in either case.

If posting, post the initial comment immediately, before doing any further investigation work, so
the engineer sees the agent picked it up:

Write the body to a file, then post it via `jq --rawfile` piped into `gh api --input -` — see
[GitHub comment lifecycle](#github-comment-lifecycle) for exactly why (a simpler-looking `-f
body=@file` silently posts the wrong thing, without erroring):

```bash
cat > /tmp/comment.md <<'EOF'
## 🔍 Bug investigation in progress

- ⏳ 1. Read the issue
- ⬜ 2. Find relevant files
- ⬜ 3. Attempt to reproduce
- ⬜ 4. Root cause analysis
- ⬜ 5. Validate the fix
- ⬜ 6. Confidence & decision

---
🤖 _This is an automated investigation, posted and updated by an AI agent (`bug-investigation`
skill). It is not a substitute for maintainer review — treat findings as a starting point, not a
verdict._
EOF

jq -n --rawfile body /tmp/comment.md '{body: $body}' \
  | gh api repos/<owner>/<repo>/issues/<issue_number>/comments --input - --jq '.id'
```

Capture the printed `.id` — this `comment_id` is reused for every update in
[Phase 0's sibling, the update step](#github-comment-lifecycle). If comment creation fails (auth,
permissions, deleted issue), stop and tell the engineer — don't investigate silently with no
visible progress. After posting, fetch it back and confirm the body matches what was intended
(see the lifecycle section) — don't just trust a 200 response.

**Every PATCH keeps this footer** (adjust only if the phase-tracking repo already has its own
disclosure convention for automated comments — this repo's triage automation uses a similar
"🤖 automatically triaged..." footer on issue comments; match that style rather than inventing a new
one per-skill). Never post without it — an AI-authored investigation must be self-identifying on
first read, not something the reader has to infer from tone or content.

### Phase 1 — Read the issue

```bash
gh issue view <issue_number> --repo <owner>/<repo> --json title,body,labels,comments,url,assignees,author
```

Read title, body, **all** comments (later comments often contain reproduction confirmations,
narrowed repro steps, or a maintainer's initial hunch — don't stop at the issue body). Classify the
bug type — this drives Phase 3's reproduction method:

| Signal in issue                                         | Bug type       | Reproduction method (Phase 3)    |
| ------------------------------------------------------- | -------------- | -------------------------------- |
| UI screenshot, Operate/Tasklist/Identity page mentioned | UI             | Playwright                       |
| REST/gRPC request/response, curl repro steps            | API/behavior   | Generated API script             |
| "flaky", "sometimes fails", CI link to a failed test    | Flaky test     | Repeat the existing test N times |
| Stack trace only, no UI/API repro path                  | Backend/engine | Code analysis + targeted unit/IT |

Read `references/triage-guidelines.md` for how severity/likelihood/urgency labels should shape the
tone and urgency of the final report.

**If the issue body, comments, or attached logs contain a fully-qualified class name, an exact log
message format string, or a stack trace**, extract it verbatim now — this is the ground-truth
identifier Phase 2 must match exactly (Hard rule 10). A component label or a vague description of
"the controller" is not a substitute for the FQCN itself when one is available.

Update the comment: check off item 1.

### Phase 2 — Find relevant files

Search the codebase for the failing behavior (component label on the issue narrows the module —
see the path map in the repo's `AGENTS.md`). Cross-reference with recent history for the same area:

```bash
git log --oneline -20 -- <suspected path>
```

Recent changes to the exact file/area are a strong root-cause lead — check whether the bug's
symptoms started appearing after one of them.

**If the affected code is query/aggregation/statistics logic reachable through more than one
storage backend**, find all the backend-specific implementations, not just the one the issue
happens to point at — per Hard rule 8, look for the RDBMS mapper (`db/rdbms/src/main/resources/
mapper/`) and the ES/OS transformer (`search/search-client-query-transformer/`) as a pair, and note
both file sets in the comment even before confirming which ones are actually broken.

**If Phase 1 extracted a verbatim identifier (Hard rule 10)** — a fully-qualified class name, log
format string, or stack trace frame — grep for that exact string first, not a fuzzy name match on
the class's short name alone. If it isn't found on `main` and the issue names a specific released
version, check the corresponding `stable/X.Y` branch (or version tag) before concluding the class
doesn't exist in the repo:

```bash
git fetch origin stable/X.Y
git ls-tree -r origin/stable/X.Y --name-only | grep <ClassName>
```

Do not substitute a similarly-named class from a different package/module as "the relevant file"
unless you've confirmed the exact identifier truly doesn't exist anywhere across `main` and the
reported version's branch. If you do fall back to an unconfirmed similar match, say so explicitly
in the comment as a guess — never present it as the finding.

Update the comment: check off item 2, list the files found so far.

### Phase 3 — Attempt to reproduce

Actually run the bug — do not just write reproduction steps. Method depends on Phase 1's
classification; see [`references/reproduction.md`](references/reproduction.md) for exact commands
(c8run startup/teardown, Playwright invocation, API script pattern).

**If Phase 2 found a multi-backend pair (Hard rule 8), reproduce against every backend the repo
supports** (RDBMS/H2, Elasticsearch, OpenSearch) via the `qa/acceptance-tests` `@MultiDbTest`
harness — see `references/reproduction.md` for the `-Dtest.integration.camunda.database.type`
invocation per backend. Do not stop at the first backend that reproduces the bug; a passing
result on one backend and a failing one on another is exactly the signal this rule exists to
catch, and both outcomes need to be in the report, not just the first one found.

Reproduction outcome feeds directly into Phase 6's confidence score:

- **Confirmed** (bug reproduced) → strong positive signal.
- **Could not reproduce** → confidence caps at Medium; flag as an explicit open question in the
  report rather than silently downgrading without explanation.
- **Environment failed to start** (c8run didn't come up, Playwright couldn't launch) → mark this
  step ❌ in the comment, continue to Phase 4 with code analysis alone. A broken local environment
  is not the same as "bug not reproducible."
- **Wrong branch/version tested** — if Phase 2's verbatim identifier (Hard rule 10) isn't present in
  the branch/build actually being exercised, a "could not reproduce" result here proves nothing
  about the reported bug. Don't score it as "could not reproduce" (which only caps confidence at
  Medium per the triage guidelines) — it's an incomplete investigation. Go check the version-specific
  branch (Phase 2) before drawing any conclusion.

**Tear down the reproduction environment now**, regardless of outcome, before moving to Phase 4 —
see `references/reproduction.md` for the teardown command. Don't defer cleanup to the end of the
whole investigation; if a later phase throws, cleanup must have already happened.

Update the comment: check off (or ⏭️/❌) item 3 with a one-line outcome.

### Phase 4 — Root cause analysis

Reason through what the code actually does versus what it should do, grounded in the file(s) found
in Phase 2 and (if reproduced) the actual failure observed in Phase 3. Name the specific
function/line where behavior diverges from intent — "somewhere in the auth flow" is not a root
cause.

Update the comment: check off item 4, summarize the root cause in 2–4 sentences.

### Phase 5 — Validate the fix

Get a real candidate fix onto disk first — the checks below require actually compiling and running
code, not reasoning about a description of it:

1. **Check `git status` first.** Note any pre-existing uncommitted changes unrelated to the fix
   (other in-progress work, this skill's own files, local config) — these must never end up
   committed as part of the fix later.
2. **Branch from `origin/main`, not local `main`.** Local `main` can be stale; branching from a
   behind `main` risks basing the fix on outdated code or missing conflicts a maintainer would hit.
   `git fetch origin main && git checkout -b <descriptive-branch-name> origin/main`. If this
   conflicts with in-progress uncommitted changes to files the fix doesn't touch, that's fine —
   checkout only fails on files with actual content differences, and the fix's own files should
   still apply cleanly if they match between local `main` and `origin/main` (verify with
   `git diff --stat main origin/main -- <fix files>` — expect no output).
3. **Implement the candidate fix** on this branch.

Only then run the fix through all six checks below. All six must pass for the fix to count toward
High confidence in Phase 6 — a fix that fails any one of these is not "mostly validated," it's
unvalidated.

1. **Static check** — does it compile/parse cleanly, are types correct?
2. **Semantic check** — does it address the root cause (not just the symptom), avoid masking the
   underlying issue, and avoid breaking adjacent behavior in the same file/method?
3. **Pattern check** — search repo history for how similar bugs were fixed before
   (`git log --grep`, similar test names) — does this fix follow the established pattern, or
   deviate from it without reason?
4. **Simulation** — mentally re-run the failing scenario from Phase 3 with the fix applied. Does it
   actually resolve what was observed? For a frontend/CSS/layout fix specifically, don't conclude
   "no test infrastructure exists to verify this" from checking only one local tool (e.g. a vitest
   browser-mode project with zero test files does not mean no visual test exists) — actively search
   for the module's CI-defined test suites first: `grep -rl "toHaveScreenshot\|visual-regression"
<module>/` and check `.github/workflows/ci-<module>.yml` for a job named "Visual Regression" or
   similar. CI workflow files are the authoritative list of what actually gates the PR, more so than
   any single local config file. If a real suite exists, run it locally before opening the PR (even
   without CI's exact container, e.g. `npm run build:visual-regression && npm run test:visual`) as a
   smoke check — catching a stale snapshot pre-push is cheaper than discovering it from a failed CI
   run after the PR is already open.
5. **Compatibility check (Hard rule 7)** — does the fix change a REST/gRPC API's request/response
   shape or status codes, rename/remove an exported type/field/CLI flag, or change an on-disk/wire
   format that existing callers depend on? Check the module's `docs/adr/` for any documented
   compatibility guarantee the fix might cross. If the fix fails this check, stop — do not implement
   it. Go back and look for a non-breaking alternative (e.g. adding rather than changing a field,
   deprecating rather than removing); if none exists, this bug caps at Low confidence and gets
   reported, not fixed (Hard rule 7).
6. **Backend-parity check (Hard rule 8)** — if Phase 2/3 identified this as query/aggregation logic
   spanning RDBMS/ES/OS, does the candidate fix cover **every** backend on which the bug reproduces
   (or is plausible), not just the one the issue happened to point at? Run the reproducing test
   against all backends per Phase 3. If the fix only addresses a subset, this check fails — do not
   open a PR with it as-is. Either extend the fix to cover the remaining backend(s) before continuing
   to Phase 6, or, if that's genuinely out of reach in this pass, treat the whole fix as not yet
   validated (not "validated for one backend") and let Phase 6 route it to report-only.

If a fix attempt fails with a structural error unrelated to the fix's own logic — "Ambiguous
`@ExceptionHandler`", a missing bean, a type that doesn't resolve, an annotation that has no effect
where you placed it — don't read that as "this bug doesn't need a code fix after all." It's far more
often a sign the fix landed on the wrong file or module (Hard rule 10) than a sign the real app
already works correctly. Re-open Phase 2 and re-confirm the file identity against the issue's
verbatim identifier before concluding no fix is needed.

Update the comment: check off item 5, note which of the six passed/failed.

### Phase 6 — Score confidence and decide

Score confidence using the table in
[`references/triage-guidelines.md`](references/triage-guidelines.md#confidence-scoring) and act on
it. Before scoring the "issue premise looks wrong" row specifically, confirm every verbatim
identifier extracted in Phase 1 (Hard rule 10) was actually matched during the investigation — not
approximated by a similarly-named class or a different endpoint. An unmatched identifier means the
investigation hasn't actually located the reported code path; that's "blocked by missing info" (the
row above), not "premise looks wrong" — the latter tells the reporter to consider closing their own
possibly-still-valid bug.

| Confidence                                                                                                              | Diff size    | Action                                                                                                                                                                                                                                                       |
| ----------------------------------------------------------------------------------------------------------------------- | ------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| High (≥ 0.80)                                                                                                           | < 1000 lines | Implement the fix, validate it, open a **draft** PR (see [PR creation](#pr-creation))                                                                                                                                                                        |
| High (≥ 0.80)                                                                                                           | ≥ 1000 lines | Treat as Medium — large diffs need human judgment regardless of confidence                                                                                                                                                                                   |
| Medium (0.50–0.79)                                                                                                      | any          | Implement + open a **draft** PR as above, and `@mention`/tag the issue owner or assignee asking them to weigh in before it's marked ready                                                                                                                    |
| Low (< 0.50) — blocked by missing info                                                                                  | any          | No PR. Post report only, explicitly list what's blocking higher confidence (missing logs, couldn't reproduce, ambiguous root cause)                                                                                                                          |
| Low (< 0.50) — issue premise looks wrong (expected behavior, already covered by another endpoint/feature, doc mismatch) | any          | No PR. Post report only, `@mention`/tag the **issue reporter** directly (not the assignee) explaining what already covers their need or why the behavior is expected, and ask them to confirm whether to close as not-a-bug or re-scope as a feature request |

If more than one independent fix is viable for **different symptoms or layers** of the same issue
(e.g. a frontend workaround alongside a backend root-cause fix, where each stands on its own even
if the other were never done), it's fine to implement a subset in the PR and call out what's left as
an explicit follow-up in both the PR description and the issue comment — don't block the PR on
finishing every angle, and don't silently implement only part of it without saying so.

This allowance does **not** extend to splitting a single root-cause fix across storage backends
(Hard rule 8). RDBMS, Elasticsearch, and OpenSearch are not "different layers" of the fix — they're
alternative deployments of the same product, and a user only ever runs one of them. A fix that's
correct on RDBMS and still wrong on ES/OS (or vice versa) isn't a partial fix with a documented
follow-up, it's a fix that silently doesn't work for some deployments. If Phase 5's backend-parity
check fails, don't open the PR with the incomplete fix "as a start" — either finish all backends
first, or don't open a PR at all and report the full multi-backend root cause instead (see the Low
confidence rows below).

Do the final PATCH with the complete report: all six phases checked off, root cause, the fix that
was implemented (or, at Low confidence, described but not applied), validation results, confidence
score with rationale, the decision above, and — once opened — the PR link. Keep the 🤖 disclosure
footer from Phase 0 — the final report is the version most people actually read, so it's the one
where self-identification matters most.

## PR creation

Only reached at Medium/High confidence (Hard rule 1). Do this after Phase 5's validated fix and
before the final Phase 0 comment PATCH, so the PATCH can include the PR link. The fix branch and
implementation already exist from Phase 5 — this section only covers committing and opening it.

1. **Re-run the module's test suite** (the reproducing test from Phase 3, plus the existing suite
   for the touched area) to confirm the reproducing test still passes and nothing else regressed
   since Phase 5's checks. Follow the repo's `AGENTS.md` build-pipeline conventions (module-scoped
   builds, `-Dquickly` for fast iteration, full pipeline before committing). **If the fix touches
   multi-backend query logic (Hard rule 8), re-run against every backend** — Phase 5's
   backend-parity check already covered this once, but re-confirm nothing changed since.
2. **Format**: `./mvnw license:format spotless:apply -T1C` for Java/XML/Markdown changes; the
   relevant `npm run format`/`prettier:format` for frontend changes (see
   `.github/instructions/frontend.instructions.md`).
3. **Stage only the fix's files by name** (never `git add -A`/`git add .`) — re-check `git status`
   and confirm nothing from Phase 5's pre-existing-changes check got swept in.
4. **Commit** following the repo's commit message conventions (Conventional Commits, body explains
   why, `Closes #<issue_number>`).
5. **Push and open the PR as a draft**: `git push -u origin <branch>`, then
   `gh pr create --draft --title "..." --body "..."`. The PR body should include: a summary of the
   root cause and fix, `Closes #<issue_number>`, the validation performed (tests run, pass/fail),
   the confidence level and its weakest input (per the triage guidelines), and — if only a subset of
   a multi-part root cause was fixed — an explicit "out of scope / follow-up needed" section.
6. **Never mark the PR ready for review** — it stays a draft; a maintainer promotes it once they've
   looked it over. Don't use `gh pr ready`.

## GitHub comment lifecycle

This section only applies if Phase 0 decided to post. If it decided not to, every "post"/"PATCH
the comment" instruction elsewhere in this skill (including "Update the comment: check off item
N" in each phase) means something different: keep a single running copy of the report body
locally — the same content a PATCH would have contained — and update it after each phase exactly
as if about to PATCH, but don't call the GitHub API. Surface only the final version once, in your
response to the engineer, at the end of Phase 6. Nothing else below applies in that case.

If posting: the comment body is always multi-paragraph markdown with permalinks, code spans, and headings —
never pass it as an inline `-f body="..."` shell string (quoting/escaping breaks on real content)
and **never use `-f body=@<path>`** expecting file-content substitution — `gh api`'s `-f`/`--raw-field`
does not do `@file` expansion the way `-F`/`--field` does for some inputs; passed this way, the
literal string `@/path/to/file` gets posted as the comment body verbatim. This is a known failure
mode: it doesn't error, so it never triggers Phase 0's "comment creation failed" guardrail — it
silently posts garbage instead. Always build the body as JSON via `jq --rawfile` and pipe it into
`gh api --input -`:

```bash
# Write the comment body to a file first (any multi-paragraph markdown), then:

# Initial comment — Phase 0, capture comment_id from the response
jq -n --rawfile body ./comment.md '{body: $body}' \
  | gh api repos/<owner>/<repo>/issues/<issue_number>/comments --input - --jq '.id'

# Update after every phase — same comment_id every time
jq -n --rawfile body ./comment.md '{body: $body}' \
  | gh api --method PATCH repos/<owner>/<repo>/issues/comments/<comment_id> --input -

# Read issue / comments (Phase 1)
gh issue view <issue_number> --repo <owner>/<repo> --json title,body,labels,comments,url,assignees,author
```

After posting or PATCHing, always fetch the comment back
(`gh api repos/<owner>/<repo>/issues/comments/<comment_id> --jq '.body'`) and confirm it matches
what was intended before moving on — don't just trust a 200 response.

Progress markers in the comment body: `⏳` in progress · `✅` complete · `⬜` not started yet ·
`⏭️` skipped, with a one-line reason · `❌` failed (e.g. environment wouldn't start).

When the comment references specific code, use a stable GitHub permalink (see "Referencing code in
issues, PRs, and comments" in the repo's `AGENTS.md`) — not a bare `path:line`, since the comment
outlives the current file state.

## Guardrails

- **Never** open a PR, commit, or push at Low confidence — investigate and report only (Hard rule 1).
- **Never** open a PR as ready-for-review — always `--draft`, never `gh pr ready` (Hard rule 1).
- **Never** commit or push anything beyond the fix's own files — check `git status` before staging,
  stage by name, never `git add -A`/`git add .` (Hard rule 2).
- **Never** branch from local `main` without first checking it against `origin/main` — a stale local
  `main` can silently base the fix on outdated code (Hard rule 2,
  [Phase 5](#phase-5--validate-the-fix)).
- **Never** skip formatting or the module's test suite before committing — follow the repo's
  `AGENTS.md` build-pipeline conventions in full, same as any other change to this repo.
- **Never** implement a fix that breaks a public API/contract, exported type, or on-disk/wire
  format — Phase 5's compatibility check must pass first; if it can't, report only (Hard rule 7).
- **Never** open a PR that fixes the bug on only some of RDBMS/Elasticsearch/OpenSearch when the
  same root cause affects the others — implement and validate every backend the bug reproduces on
  first, or don't open the PR at all (Hard rule 8, Phase 5's backend-parity check).
- **Never** post a second top-level comment for progress — always PATCH the one from Phase 0.
- **Never** block on missing logs (Hard rule 4).
- **Never** conclude "issue premise is wrong" or "no fix needed" without matching every verbatim
  identifier (FQCN, log message, stack trace) the issue provided — a near-miss match in a different
  module is not confirmation, and treating it as one has previously led to telling a reporter their
  bug wasn't real when it still reproduced on every supported release branch (Hard rule 10).
- **Never** leave a reproduction environment running after Phase 3, even on failure or early exit.
- If the issue is already closed, or already has a linked PR, stop and tell the engineer before
  investigating — don't duplicate in-flight work. Checked at the very start of Phase 0, before any
  comment is posted.

## Compose with

- `ci-fix-failure` — if the issue turns out to be a CI/workflow failure rather than a product bug,
  hand off; this skill is for product/runtime bugs.
- `create-issue` — not used by this skill (it operates on an existing issue), but shares the
  repo's `gh` conventions.
