# AI-Assisted Code Quality Pipeline — Hackday Plan

**Status:** Proposal — pending team sync
**Authors:** Nikola Koevski + 1 (TBD)
**Scope:** Hackday experiment, with a path to graduate into official Camunda CI if it proves stable and useful.

---

## 1. Goal

Reduce the human triage bottleneck that typically kills static-analysis pipelines by having an AI agent classify and act on findings:

- Identify high-risk areas of the codebase (churn × complexity hotspots).
- Run broad-coverage static analysis on those areas.
- Have an AI agent classify each new finding into one of two tracks:
  - **PR track** — AI generates a fix; pipeline opens a PR with a human reviewer required to merge.
  - **Issue track** — AI writes a structured issue for human triage.
- Use **Infer as a verification gate** on AI-generated PRs, catching subtle bugs (races / NPE / leaks) the AI may have introduced.

The endgame is *automated remediation of the long tail of code-quality issues*, with humans focused on review rather than triage.

---

## 2. Pipeline overview

```
┌─────────────────────────────────────────────────────────────────────┐
│ Stage 1: Hotspot identification                       (weekly)      │
│   code-maat / CodeScene → ranked file list (churn × complexity)     │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│ Stage 2: Static analysis                              (nightly)     │
│   CodeQL scan (security-and-quality pack), scoped via `paths:`      │
│   config to top-N hotspots. Native GitHub Action; no token needed.  │
│   Output: SARIF, diffed against last run's baseline                 │
│   → only NEW findings flow downstream                               │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│ Stage 3: AI triage (Claude)                                         │
│   For each new finding: read rule + code context, classify          │
│   Output: { pr_eligible: [...], issue_only: [...] }                 │
└─────────────────────────────────────────────────────────────────────┘
                  │                                       │
        pr_eligible                              issue_only
                  ▼                                       ▼
┌────────────────────────────────────┐   ┌──────────────────────────────┐
│ Stage 4a: PR track                 │   │ Stage 4b: Issue track        │
│  1. AI generates fix               │   │  AI writes a structured      │
│  2. Create branch, apply diff      │   │  issue: rule, location,      │
│  3. mvn install -pl <module> -am   │   │  why-not-auto-fix, suggested │
│     -Dquickly + module tests       │   │  approach.                   │
│  4. spotless:apply + license       │   │  Tag: `ai-triage:auto-       │
│  5. Open PR, assign reviewer       │   │        detected`             │
│     Label: `ai-fix:auto-pr`        │   │  Component owner assigned.   │
│  6. Human review REQUIRED before   │   │                              │
│     merge (branch protection)      │   │                              │
│  (Infer gate deferred — see §3)    │   │                              │
└────────────────────────────────────┘   └──────────────────────────────┘
```

**Key design choices:**

- **Hotspot-first scoping** keeps Stage 2 tractable on a monorepo of this size and biases toward bugs in actively-developed code.
- **Two-track output** prevents the most common failure mode of AI auto-fix systems: plausible-looking fixes for architectural / concurrency bugs that introduce subtle regressions.
- **Infer is a *gate*, not a scanner** — it analyses only the small AI-generated diff, exploiting its strength (deep race / NPE / leak detection) without paying its full-scan cost. **Deferred for the hackday MVP** — see §3 Stage 4a.
- **Human review is mandatory** via branch protection. The pipeline never merges autonomously.

---

## 3. Stages in detail

### Stage 1 — Hotspot identification

**Tool:** `code-maat` (free, CLI, CSV out) for hackday. CodeScene is a future upgrade if we get a license.

**Inputs:** `git log --no-merges --numstat --format='%H,%an,%ad,%s'` over the last N months.

**Outputs:** ranked file list with churn × complexity score, written to `hotspots.csv`.

**Frequency:** weekly. Cached as a workflow artifact; downstream stages read it.

**Hackday simplification:** if `code-maat` setup proves fiddly, hardcode a starting module (e.g. `zeebe/engine`) and skip Stage 1 for the demo. Add Stage 1 in a follow-up.

### Stage 2 — Static analysis (CodeQL)

**Tool:** [CodeQL](https://codeql.github.com/) via the [`github/codeql-action`](https://github.com/github/codeql-action) GitHub Action.

**Why CodeQL over SonarQube:**

- **Free for public repos** (Camunda is OSS) — no SonarCloud token / self-hosted infra needed.
- **GitHub-native:** runs as a GitHub Action with `GITHUB_TOKEN`; results land in the Security tab and PR annotations out of the box.
- **SARIF by design** — same downstream contract for the AI triage stage.
- **Inter-procedural + taint analysis** included in the `security-and-quality` query pack — covers SAST findings that SonarQube Community Edition would miss.
- Prior art: GitHub's own Copilot Autofix consumes CodeQL findings, validating the architecture.

**Query packs to use:**
- `security-and-quality` — broad rule set, includes maintainability + security.
- (Optional) `security-extended` if `security-and-quality` is too noisy for the AI to triage.

**Build mode:** `manual` build mode with `./mvnw install -pl zeebe/engine -am -Dquickly -T1C` (CodeQL needs to observe the compile step). `autobuild` is unlikely to handle this monorepo cleanly — verify in Phase 0.

**Scoping:** CodeQL config file (`.github/codeql/codeql-config.yml`) with `paths:` set to the hotspot file list from Stage 1 (or a hardcoded module path).

**Incrementality:** baseline SARIF stored as a workflow artifact; subsequent runs only emit *new* findings (delta). For hackday, baseline can be empty (everything is "new") on first run.

**Tradeoffs to be aware of:**
- CodeQL builds a database before querying; first run on `zeebe/engine` may take 5–10 minutes.
- Custom rules require learning QL — out of scope for hackday; we rely on the default packs.

### Stage 3 — AI triage

**Runtime:** Claude Code Action ([github.com/anthropics/claude-code-action](https://github.com/anthropics/claude-code-action)) once we have an API key. Without a key, fallback is a shell stub (see "Fallbacks" below).

**Input:** SARIF/JSON of new findings + relevant source-file snippets.

**Output:** structured JSON:

```json
{
  "pr_eligible": [
    { "finding_id": "...", "file": "...", "rule": "java/deprecated-call",
      "fix_summary": "...", "confidence": 0.9 }
  ],
  "issue_only": [
    { "finding_id": "...", "file": "...", "rule": "java/resource-not-closed",
      "reason": "Resource leak crosses method boundaries — needs human review",
      "suggested_approach": "..." }
  ]
}
```

**Classification rubric (encoded in the AI prompt):**

A finding is **PR-eligible** only if **all** of the following hold:
- Local fix (single function or file, no cross-module impact).
- Not concurrency-related (no locks, threads, async, `volatile`, `CompletableFuture`).
- Not security-sensitive (not a SAST or taint rule).
- High-confidence rule type (e.g., deprecated API rename, unused import, simple null check, missing `@Override`).
- File has existing test coverage (so the fix can be validated mechanically).

A finding is **issue-only** if **any** of:
- Architectural or cross-cutting.
- Concurrency, threading, or async correctness.
- SAST / taint analysis finding.
- Low-confidence or context-dependent rule.
- File has no test coverage.

When in doubt → issue-only.

### Stage 4a — PR track

For each PR-eligible finding:

1. AI generates the fix (Claude via AWS Bedrock).
2. Create branch `ai-fix/<finding-id>`; apply the diff.
3. Run `./mvnw install -pl <module> -am -Dquickly -T1C` followed by module tests.
4. Run `./mvnw license:format spotless:apply -T1C`.
5. Open PR; auto-assign a CODEOWNERS reviewer; apply label `ai-fix:auto-pr`.
6. Branch protection enforces approving review on `main`.

**Volume cap:** max 5 auto-PRs per day during hackday to prevent flooding the review queue. Configured as a job-level guard.

**Infer verification gate — deferred.** The plan originally had Infer running on the AI-generated diff to catch fixes that look right but introduce subtle races, NPEs, or leaks. For the hackday MVP, the only PR-eligible rule is `java/deprecated-call` (a pure rename to a non-deprecated successor), which essentially cannot introduce that class of bug. The mandatory human review covers the remaining risk. Light up the Infer gate when the PR-eligible allowlist expands beyond pure renames into rules where Infer's depth pays off (resource leaks, complex null derefs).

### Stage 4b — Issue track

For each issue-only finding:

1. AI writes structured issue body: rule, location, AI's analysis of *why* it's not auto-fixable, suggested approach.
2. Title format: `[ai-triage] <rule-id>: <short summary> — <file>`.
3. Labels: `ai-triage:auto-detected`, plus a severity label inherited from CodeQL.
4. Component owner auto-assigned via CODEOWNERS lookup.

---

## 4. Hackday MVP — concrete deliverables

What we build in 1–2 days:

1. **GitHub Actions workflow** `.github/workflows/code-quality-ai.yml` (manual dispatch + scheduled).
2. **CodeQL setup**: `github/codeql-action` invocations in the workflow with a `codeql-config.yml` scoped to `zeebe/engine` and the `security-and-quality` query pack.
3. **AI triage script** (Python): consumes SARIF, classifies findings, emits JSON. Stub or real Claude depending on key availability.
4. **PR-track runner**: for each PR-eligible finding, generate fix → run module build + tests → spotless → open PR. (Infer gate deferred; see §3.)
5. **Issue-track runner**: opens GitHub issues from the AI's structured output.
6. **Demo write-up** showing input findings → classification → resulting PRs and issues, with sample output.

**Initial scope guards:**

- One module: `zeebe/engine`.
- One CodeQL rule for the PR track: `java/deprecated-call` ("call to deprecated method") — pure renames to the non-deprecated successor, easiest to validate.
- Issue-only for everything else.
- 5 auto-PRs/day cap.
- 10 auto-issues/day cap.

---

## 5. Implementation plan (phased, two-person team)

### Phase 0 — Pre-work (before hackday, ~1 hr each)

| Task | Owner |
|---|---|
| Create worktree `camunda-worktree-ai-code-quality` and feature branch | Nikola |
| Run CodeQL `github/codeql-action` locally against `zeebe/engine` to confirm `manual` build mode works (annotation processors, Spring Boot) | TBD |
| Identify a CODEOWNERS path for `zeebe/engine` | TBD |
| Decide whether to seek a Claude API key before hackday | Together |

### Phase 1 — Static analysis loop (hackday Day 1 morning)

Person A (Nikola): CodeQL + workflow plumbing
- Workflow scaffolding `code-quality-ai.yml`
- `github/codeql-action/init` + `analyze` steps with the `security-and-quality` pack
- `codeql-config.yml` scoped to `zeebe/engine`, manual build via `./mvnw install -pl zeebe/engine -am -Dquickly -T1C`
- SARIF artifact upload (in addition to the default Security-tab upload)

Person B: Hotspot stub + repo helpers
- Hardcoded hotspot list (one module) + interface for future code-maat integration
- GitHub API helpers (issue/PR creation, CODEOWNERS lookup) as a small Python module

**Exit criterion:** workflow run produces a SARIF with at least one `java/deprecated-call` finding from `zeebe/engine`.

### Phase 2 — AI triage stub (hackday Day 1 afternoon)

Person A: PR-track scaffolding
- Volume cap helper, branch-naming convention, draft PR body templating
- Infer verification gate is deferred (see §3); do not build it for the MVP

Person B: AI triage script
- Without Claude API key: rule-ID-based classifier (`java/deprecated-call` → PR-eligible; everything else → issue-only)
- With Claude API key: Claude Code Action invocation with the rubric prompt
- Output: structured JSON

**Exit criterion:** SARIF in → JSON classification out, deterministic for the same input.

### Phase 3 — Track runners (hackday Day 2 morning)

Person A: PR track runner
- Branch creation, diff application, build + test run, spotless, PR open
- Volume cap enforcement

Person B: Issue track runner
- Issue body templating from classification output
- Labels, CODEOWNERS-based assignment
- Volume cap

**Exit criterion:** end-to-end run on `zeebe/engine` produces at least one demo PR and one demo issue.

### Phase 4 — Demo + write-up (hackday Day 2 afternoon)

- Run the pipeline on `zeebe/engine` with real findings.
- Capture metrics: findings classified, PRs opened, build failures.
- Write up the demo and propose graduation criteria.

---

## 6. Fallbacks (no Claude token)

CodeQL needs no token (it uses `GITHUB_TOKEN` automatically on public repos), so the Sonar token blocker is gone. Only the Claude side needs a fallback.

| Capability | Fallback |
|---|---|
| AI triage | Rule-ID-based classifier for the demo (`java/deprecated-call` → PR-eligible, all else → issue-only); plumbing is identical to the AI version, so swap is one-line |
| AI fix generation | If no Claude key: skip auto-fix in the PR track — open a draft PR with the finding location and a TODO marker for a human to fix. Less impressive demo but proves the rest of the pipeline. |

---

## 7. Open decisions for team sync

1. **CodeQL query pack:** start with `security-and-quality` (broad) or `security-extended` (narrower, security-focused) for the first run?
2. **Claude API key:** push to get one before hackday, or accept the rule-ID classifier fallback?
3. **AI runtime:** Claude Code Action vs custom script invoking the Claude API directly?
4. **Initial module:** stick with `zeebe/engine`, or pick a less concurrency-dense module (e.g., `service/` or `clients/`) where AI fixes are lower-risk?
5. **Auto-PR cap:** is 5/day acceptable to the team, or stricter for hackday?
6. **Bot identity:** dedicated GitHub App vs a service-account PAT? Permissions scope?
7. **Where do auto-PRs land:** main repo, or a fork during hackday to avoid noise?

---

## 8. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| AI generates plausible-looking fixes that are subtly wrong | High | Two-track classification + Infer gate + mandatory human review |
| Auto-PRs flood the review queue | Medium | Volume cap (5/day) + dedicated label so they're easy to filter |
| CodeQL `manual` build mode fails on the monorepo (annotation processors, Spring Boot) | Medium | Run Phase 0 sanity check locally before hackday; fall back to `autobuild` or scope-down |
| `security-and-quality` pack produces too many findings to triage on first run | Medium | Run with empty baseline first to size the volume; fall back to `security-extended` if overwhelming |
| Infer gate not in place when PR allowlist expands | Low (now) → High (later) | Deferred for MVP; revisit before adding non-rename rules to the PR-eligible allowlist |
| Bot has too much repo write access | Medium | Scoped GitHub App; cannot bypass branch protection; cannot write to `main` |
| Cost (Claude API tokens) | Low for hackday | Cap on number of triages per run; usage observed in demo |
| Reviewer fatigue if PRs are low-quality | Medium | Start with one rule type (`java/deprecated-call` — deprecated rename); expand based on merge rate |

---

## 9. Success metrics for the demo

- ≥ 1 AI-generated PR opens against `zeebe/engine` and passes the module build + tests.
- ≥ 1 issue is created for an architectural finding with a useful AI analysis.
- 0 PRs merged without human review (mandatory branch protection).
- Demo write-up captures: total findings → classification split → PRs opened → build pass/fail.

**Graduation criteria** (post-hackday, before this becomes part of `ci.yml`):

- Auto-PR merge rate ≥ 60% over a 2-week window on the chosen rule set.
- Auto-PR revert rate < 5%.
- Issue-track issues are being acted on (closed/triaged) by component owners.
- CodeQL finding diff produces a manageable volume (≤ 20 new findings/day across the chosen modules).

---

## 10. Out of scope (explicitly)

- The flaky-test detection pipeline that was previously discussed (separate concern, separate proposal).
- Replacing existing Camunda CI checks. This pipeline is *additive*, not a replacement.
- Cross-language analysis (we focus on Java; frontend code is out of scope).
- Auto-merge: a human always merges. Even when the bot's work is perfect.
