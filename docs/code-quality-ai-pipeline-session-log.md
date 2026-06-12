# Code Quality AI Pipeline — Session Log

**Companion to** [`code-quality-ai-pipeline-plan.md`](./code-quality-ai-pipeline-plan.md). The plan covers the **design**; this file captures the **decisions, adjustments, and open items** from the implementation session so a teammate can resume without re-deriving context.

---

## 1. Where the session left off

- Branch: `feat/ai-code-quality-pipeline` (12 commits ahead of `main`).
- Last pushed: only the first 7 commits (up through `9b26a7b ci: disable nightly schedule`). The remaining 5 commits are local and need a push before the teammate sees them. **See §8.**
- Workflow: `workflow_dispatch`-only (cron commented out for team review).
- Worktree: `../camunda-worktree-ai-code-quality` (sibling to the main repo checkout).
- Pipeline shape: `hotspots → codeql-scan → ai-triage → {pr-track, issue-track}`.
- All 8 implementation steps from the original plan are complete (Step 5 deferred — see §5).

### Files added by this session

| Path | Purpose |
|---|---|
| `docs/code-quality-ai-pipeline-plan.md` | The shareable team plan (design, MVP scope, risks). |
| `docs/code-quality-ai-pipeline-session-log.md` | This file. |
| `.github/workflows/code-quality-ai.yml` | The full pipeline workflow (5 jobs). |
| `.github/codeql/codeql-config.yml` | Static fallback CodeQL config (the workflow generates a dynamic one at runtime). |
| `.github/scripts/code-quality-ai/hotspots.js` | Stage 1 — code-maat-driven hotspot identification. |
| `.github/scripts/code-quality-ai/triage.js` | Stage 3 — rule-ID + Bedrock classifier. |
| `.github/scripts/code-quality-ai/pr_runner.js` | Stage 4a — generates fix via Bedrock, opens PR. |
| `.github/scripts/code-quality-ai/issue_runner.js` | Stage 4b — opens auto-triage issues from a template. |
| `.github/scripts/code-quality-ai/github_helpers.js` | Shared GitHub API helpers (issue/PR creation, CODEOWNERS lookup). |
| `.github/scripts/code-quality-ai/package.json` + lock | Node deps: `@anthropic-ai/bedrock-sdk`, `@octokit/rest`. |
| `.github/scripts/code-quality-ai/.gitignore` | `node_modules/`. |

---

## 2. What remains to be done

A scannable checklist before the hackday demo. Details and rationale live in later sections; cross-references in parentheses.

### 2.1 Required to dry-run

- [ ] **Push the local commits.** Commits 8–14 (`f2d9bab`…`9a85bdb`) are still local. (§8.2)
- [ ] **Provision the four AWS / Bedrock GitHub secrets** — without these, the `ai-triage` and `pr-track` jobs will fail with an actionable error. (§8.3)
  - `AWS_REGION`
  - `AWS_ACCESS_KEY_ID`
  - `AWS_SECRET_ACCESS_KEY`
  - `BEDROCK_INFERENCE_PROFILE_ARN`
  - (`VAULT_ADDR` / `VAULT_ROLE_ID` / `VAULT_SECRET_ID` are existing repo secrets — no action.)
- [ ] **Identify a CODEOWNERS path for `zeebe/engine`** so auto-PRs land with a sensible reviewer. (Plan doc §5 Phase 0)
- [ ] **Confirm CodeQL `manual` build mode** can capture our build cleanly (annotation processors, Spring Boot). First `codeql-scan` run is the test. Fall back to `autobuild` if it fails. (Risks §8 in plan doc)
- [ ] **Trigger the workflow once via `workflow_dispatch`** and walk the artifacts. (§8.4–§8.5)

### 2.2 Required before re-enabling the cron

- [ ] **Idempotency on issue creation** — currently re-runs duplicate issues. Add a search-issues-by-finding-id check in `issue_runner.js`. (§7)
- [ ] **Idempotency on PR creation** — branches are unique per run, but multiple runs can produce duplicate PRs for the same finding. Add a search-PRs-by-finding-id check in `pr_runner.js`. (§7)
- [ ] **Cross-run volume cap state** — current caps are per-run. Push to a persistent store (workflow output, dashboard, or comment on a tracking issue) before the cron re-fires. (§7)
- [ ] **Re-enable the schedule** — uncomment the `schedule:` block in `code-quality-ai.yml`. (§3 — `Schedule` row)

### 2.3 Required before adding rules beyond `java/deprecated-call`

- [ ] **Re-light the Infer verification gate.** Pure renames don't need it; anything else (resource leaks, complex null derefs, races) does. (§5.2, plan doc §3 Stage 4a)
- [ ] **Tighten the compile gate.** Switch from `-Dquickly` (skip tests) to `verify -DskipTests=false -DskipITs` for the changed module. (§7)
- [ ] **Tighten Spotless scope.** Currently formats the whole monorepo; restrict to changed modules for speed. (§7)
- [ ] **Update `triage.js` `RULE_TRACK`** to add the new rule, and update `pr_runner.js`'s `FIX_SYSTEM_PROMPT` if the fix shape needs guidance. (§9)
- [ ] **Update the plan doc** to reflect the broader allowlist scope. (§8.7)

### 2.4 Quality / cost improvements (optional)

- [ ] **Prompt caching on Bedrock calls.** System prompts in `triage.js` and `pr_runner.js` are stable across all findings; adding `cache_control: {type: "ephemeral"}` would yield a high cache hit rate at low effort. (§7)
- [ ] **Replace template-based issue bodies with a Claude pass** if reviewers find the templates weak in practice. Plumbing parallels `pr_runner.js`. (§5.9, §7)
- [ ] **Wire Grafana flaky-test signal into Stage 1.** Combined `revisions × LOC × flakiness` ranking — see plan doc memory of decisions. Deferred until the initial pipeline is stable. (§5.6)

### 2.5 Known limitations (acceptable for the hackday demo)

These are intentional shortcuts; surface them in the demo write-up so the team knows the edges of the MVP.

- **Only one rule (`java/deprecated-call`) on the PR-eligible allowlist.** Pure rename — minimal blast radius.
- **Only one module (`zeebe/engine`) in scope** for hotspots and CodeQL. `hotspots.js --path-prefix "^zeebe/engine/"` is the gate; widening it requires reviewing build implications.
- **No idempotency** on issues or PRs (see §2.2).
- **No prompt caching** on Bedrock calls (see §2.4).
- **No retry / backoff** on Bedrock or GitHub API calls — first failure aborts the finding. Acceptable at hackday volumes (≤ 5 PRs + 10 issues per run).
- **Compile gate skips tests** (`-Dquickly`). Pure renames break at compile time, so this catches most failures, but unit-test regressions are not caught until the human review on the PR.
- **Issue body is template-based**, not AI-generated. Triage's `reason` field is the only AI content in the issue.
- **Infer verification gate is not implemented** (deferred — §5.2).
- **`security-and-quality` query pack is broad** — first run may produce a high finding count. Switch to `security-extended` if overwhelming.
- **No fork-PR support** — the workflow assumes the repo's `secrets.AWS_*` are accessible, which they are not from a fork. Fine for internal hackday use; would need rework before a public rollout.
- **CodeQL build mode is `manual`** — if the build setup changes (annotation processors, Spring Boot config), the scan can break in non-obvious ways. Mitigation: the `hotspots` job uploads the dynamic config so failures are reproducible.

---

## 3. Key decisions and rationale

These are the non-obvious choices. The plan doc documents *what* was chosen; this section documents *why* — useful when the teammate wonders whether to revisit a decision.

### 3.1 CodeQL chosen over SonarQube

Originally the plan called for SonarQube (Stage 2). Swapped to CodeQL because:

- **Free for OSS / public repos** — no SonarCloud token to chase, no self-hosted infra to spin up.
- **GitHub-native** — runs as a GitHub Action with `GITHUB_TOKEN`; results land in the Security tab automatically.
- **SARIF by design** — same downstream contract for the AI triage stage.
- **Inter-procedural + taint analysis** included in `security-and-quality`. SonarQube Community Edition (the free fallback) does **not** include Java SAST/taint, which would have weakened the "broad coverage" claim materially.
- Prior art: GitHub's own Copilot Autofix consumes CodeQL findings, validating the architecture.

### 3.2 Infer repositioned as a verification *gate*, not a scanner

In an early discussion the plan was to run Infer in parallel with Sonar/CodeQL as a primary analyzer. That changed to: Infer runs on the *small AI-generated diff* in the PR-track only, exploiting its strength (deep race / NPE / leak detection on changed code) without paying the full-scan cost.

**Then deferred entirely for the hackday MVP** — see §5.

### 3.3 Claude through AWS Bedrock (not the direct Anthropic API)

Camunda has Bedrock credentials available; using Bedrock keeps the integration inside the existing AWS account. Implemented via `@anthropic-ai/bedrock-sdk`. The inference profile ARN goes in the `model` field directly. AWS credentials are read from the standard `AWS_*` env vars by the SDK.

### 3.4 Two-track classification with a strict allowlist for the PR track

Critical decision to prevent the most common failure mode of AI auto-fix systems: plausible-looking fixes for architectural / concurrency bugs that introduce subtle regressions. **Only `java/deprecated-call`** is on the PR-eligible allowlist for the hackday. Everything else routes to the issue track for human triage.

### 3.5 Mandatory human review on every auto-PR

Branch protection enforces approving review on `main`. The pipeline never auto-merges. This is the safety net behind the deferred Infer gate.

### 3.6 Hotspot ranking is real, not stubbed

Stage 1 was originally planned as a hardcoded list. Mid-session, replaced with a real `code-maat` integration: git log → `revisions` analysis → rank by `revisions × LOC`. The output drives the CodeQL `paths:` config dynamically.

### 3.7 `gh release download` over a marketplace action for code-maat

For "less custom code" we considered `robinraju/release-downloader`, but `gh` is **pre-installed on every GitHub-hosted runner** and we already implicitly trust it (it's the `git`/`gh` for the bot). Net: one fewer marketplace action to vet and pin.

### 3.8 JavaScript over Python for the runner scripts

User preference — keeps the toolchain consistent with the GitHub Actions ecosystem (Node is everywhere; Python adds a setup-python step). Originally written as Python stubs; ported in commit `e7bc53c`.

### 3.9 Issue runner is template-based, not AI

The triage stage's `reason` field already captures the classifier's "why this is not auto-fixable" analysis. A second Claude call to write the issue body would double cost for marginal value. Deferred as a possible v2 once the demo proves the pipeline.

### 3.10 Test files are *not* excluded from CodeQL paths

Original `paths-ignore` had `**/src/test/**`. Removed at the user's request — the pipeline should also surface issues in the test suite. (See `paths-ignore` in `.github/codeql/codeql-config.yml`.)

---

## 4. Operational configuration

| Setting | Value | Where |
|---|---|---|
| Initial scan target | `zeebe/engine` | `hotspots.js --path-prefix "^zeebe/engine/"` and `gh release download` for code-maat |
| Hotspot window | last 90 days | `hotspots.js --since "90 days ago"` |
| Hotspot top-N | 50 | workflow `--top 50` |
| Auto-PR cap | 5/run | `pr_runner.js --max-prs 5` |
| Auto-issue cap | 10/run | `issue_runner.js --max-issues 10` |
| PR-eligible rule | `java/deprecated-call` only | `triage.js` (`RULE_TRACK` map) and the system prompt for Claude |
| Schedule | disabled | `code-quality-ai.yml` (cron commented out) |
| Bot git identity | `camunda-ci-bot <camunda-ci-bot@users.noreply.github.com>` | `pr-track` job, `Configure git identity` step |
| Branch naming | `ai-fix/<finding-id>-<run-id>` | `pr_runner.js`, `buildBranchName()` |
| PR label | `ai-fix:auto-pr` | `pr_runner.js` |
| Issue label | `ai-triage:auto-detected` | `issue_runner.js` |

---

## 5. Adjustments from the original plan

### 5.1 SonarQube → CodeQL (Stage 2)

Plan §3 Stage 2 originally specified SonarQube + a hosting decision matrix. Swapped to CodeQL; rule references in the plan changed from `java:S1874` → `java/deprecated-call`. See §3.1.

### 5.2 Infer verification gate deferred

Plan §3 Stage 4a originally had Infer as step 4 of the PR track. Deferred for the hackday because:
- The only PR-eligible rule (`java/deprecated-call`) is a pure rename, which essentially can't introduce a race / NPE / leak — the bug classes Infer is good at catching.
- Mandatory human review covers the residual risk.

Plan doc updated in commit `358f3b4` to mark Stage 4a step 4 as deferred. **The architectural intent is preserved**: the gate lights up when the PR-eligible allowlist expands beyond pure renames. See plan doc §3 Stage 4a.

### 5.3 Stage 1 (hotspots) became a real implementation, not a stub

Original Phase 1 plan: hardcoded module list as a placeholder. Implementation: real `code-maat` integration, with `gh release download` provisioning the JAR in CI.

### 5.4 Workflow integration of code-maat is via `gh release download`, not a marketplace action

The plan doc implies `robinraju/release-downloader`. The implementation uses `gh release download`. Both achieve "no custom JS download code" — `gh` is simpler. See §2.7.

### 5.5 Triage and PR-track runner pulled forward

Plan §5 Phase 3 had both runners landing on hackday Day 2. They're already implemented and committed. Phase 3 is essentially done before the hackday starts.

### 5.6 Grafana flaky-test integration deferred

Discussed early in the session. Conclusion: yes, it would enrich the hotspot signal (combined ranking by churn × complexity × flakiness), but Stage 1 first needs to be stable. Deferred to v2 of the pipeline.

---

## 6. Step-by-step implementation log

| # | Commit | Step | Notes |
|---|---|---|---|
| 1 | (worktree only) | 1 | Worktree at `../camunda-worktree-ai-code-quality`, branch `feat/ai-code-quality-pipeline`. |
| 2 | `0980d53` | — | docs: plan doc landed in `docs/`. |
| 3 | `fd99fa1` | 2 | CodeQL workflow scaffold + static config. Action SHAs match existing repo pins. |
| 4 | `c46e3a1` | 3 | Hotspot stub + GitHub helpers (Python — superseded by next commit). |
| 5 | `e7bc53c` | 3 | Port to JavaScript (ESM) at user request. |
| 6 | `2e0a206` | 3 | Real code-maat integration (in-script JAR download + cache). |
| 7 | `cd0a4da` | 3 | Removed in-script JAR download; script now reads `CODE_MAAT_JAR` env var. |
| 8 | `9b26a7b` | — | Disabled cron schedule before pushing for team review. |
| 9 | `f2d9bab` | 4 | Triage script: rule-ID classifier + Claude placeholder. |
| 10 | `368bb4e` | 4 | Wired Claude through Bedrock; added `ai-triage` workflow job. |
| 11 | `358f3b4` | 5 | docs: Infer gate deferred for the hackday MVP. |
| 12 | `d9fb218` | 6 | PR-track runner + `pr-track` workflow job. |
| 13 | `3bd4196` | 7 | Issue-track runner + `issue-track` workflow job. |
| 14 | `9a85bdb` | 8 | Stage 1 wired into CodeQL via dynamic config + `gh release download` for code-maat. |

Commits 1–7 are pushed to `origin`. Commits 8–14 are local. **See §8 for the push step.**

---

## 7. Deferred / known gaps (full detail)

| Item | Status | Trigger to revisit |
|---|---|---|
| Infer verification gate | Deferred | When the PR-eligible allowlist expands beyond pure renames (rule classes where Infer's depth pays off — resource leaks, complex null derefs, races). |
| Grafana flaky-test signal in Stage 1 | Deferred | After the initial pipeline is proven stable. Integration point is Stage 1 hotspot ranking, not Stage 2. |
| Issue body is template-based | Intentional for hackday | If the templates feel weak in practice, swap to a second Claude pass (similar plumbing to `pr_runner.js`). |
| No idempotency on issue creation | Acceptable for `workflow_dispatch`-only mode | Add a search-issues-by-finding-id check before re-enabling cron. |
| No idempotency on PR creation | Each run uses a unique branch (`ai-fix/<id>-<run-id>`) so collisions are impossible, but multiple runs *can* produce duplicate PRs for the same finding. | Same as issues — add a search before re-enabling cron. |
| No prompt caching on Bedrock calls | Hackday has low call volume | Add `cache_control: {type: "ephemeral"}` on the system prompts in `triage.js` and `pr_runner.js` once token costs become visible. The system prompts are stable across all findings → high cache hit rate. |
| Compile gate is `-Dquickly` (no tests) | Intentional — pure renames are caught at compile time | When the allowlist expands, switch to `verify -DskipTests=false -DskipITs` for the affected module. |
| Spotless / license:format runs over the entire monorepo | Acceptable for hackday | Restrict to the changed module(s) for speed. |
| No volume cap state across runs | Each run is independent | Push the cap to a dashboard or persistent store before re-enabling cron. |

---

## 8. How to resume / next actions for the teammate

### 8.1 Pull the latest

```sh
cd /path/to/camunda
git fetch origin feat/ai-code-quality-pipeline
git worktree add ../camunda-worktree-ai-code-quality feat/ai-code-quality-pipeline
cd ../camunda-worktree-ai-code-quality
```

### 8.2 Push the local commits (if not yet pushed)

The session ended with **commits 8–14 (`f2d9bab` through `9a85bdb`) still local**. Verify with:

```sh
git log --oneline origin/feat/ai-code-quality-pipeline..HEAD
```

If the list is non-empty, push:

```sh
git push -u origin feat/ai-code-quality-pipeline
```

### 8.3 Provision repository secrets

The workflow consumes:

| Secret | Source |
|---|---|
| `AWS_REGION` | New — hackday |
| `AWS_ACCESS_KEY_ID` | New — hackday |
| `AWS_SECRET_ACCESS_KEY` | New — hackday |
| `BEDROCK_INFERENCE_PROFILE_ARN` | New — hackday (Camunda's Bedrock inference profile) |
| `VAULT_ADDR` / `VAULT_ROLE_ID` / `VAULT_SECRET_ID` | Existing repo secrets — already in place |
| `GITHUB_TOKEN` | Provided automatically by GitHub Actions |

Add the four `AWS_*` / `BEDROCK_*` secrets at **Settings → Secrets and variables → Actions** before triggering the workflow.

### 8.4 Trigger a dry run

```sh
gh workflow run code-quality-ai.yml --ref feat/ai-code-quality-pipeline
gh run watch
```

Or via the GitHub UI: **Actions → Code Quality AI Pipeline → Run workflow → branch `feat/ai-code-quality-pipeline`**.

### 8.5 Inspect the artifacts

The workflow uploads four artifacts:

- `hotspots` — `hotspots.tsv` + the generated `codeql-config-dynamic.yml`. Confirm the paths look reasonable.
- `codeql-sarif` — raw CodeQL findings.
- `triaged-findings` — `triaged.json`, the classifier output. Confirm the split into `pr_eligible` / `issue_only` looks right.
- `pr-track-summary` and `issue-track-summary` — JSON of what was opened / skipped.

### 8.6 First-run hazards to watch

- **CodeQL `manual` build mode failing** on annotation processors / Spring Boot. Documented as a Phase 0 sanity check in the plan; verify by reading the `codeql-scan` job logs.
- **`security-and-quality` pack producing too many findings**. If the artifact is huge, switch to `security-extended` in `.github/codeql/codeql-config.yml` (and the dynamic config generator in the `hotspots` job).
- **AI fix generation producing ambiguous `old_string`** (multiple occurrences). The runner skips the finding with a recorded reason; check `pr-track-summary` for the count.
- **Bedrock rate limits** if the SARIF has many findings. The classifier walks them sequentially. If rate-limited, lower `--max-prs` or add a retry loop.

### 8.7 If you want to expand the PR-eligible allowlist

The single source of truth is `RULE_TRACK` in `.github/scripts/code-quality-ai/triage.js`. To add a rule:

1. Add it to the `RULE_TRACK` map.
2. **Re-enable the Infer verification gate** before adding any rule whose fix could plausibly introduce a race, NPE, or leak. The gate is the safety net for non-rename rules.
3. Update the system prompt in `pr_runner.js` (`FIX_SYSTEM_PROMPT`) if the rule needs different fix guidance.
4. Update the plan doc to reflect the broader scope.

---

## 9. Pointers into the source

| Question | File / function |
|---|---|
| How is the classification rubric encoded for Claude? | `triage.js`, `CLASSIFICATION_SYSTEM_PROMPT` |
| How is the fix prompt structured? | `pr_runner.js`, `FIX_SYSTEM_PROMPT` |
| How are CODEOWNERS resolved to reviewers? | `github_helpers.js`, `lookupCodeowners()` + `pr_runner.js` (team vs user split) |
| Where does the dynamic CodeQL config get generated? | `code-quality-ai.yml`, `hotspots` job → `Generate dynamic CodeQL config` step |
| Where does Bedrock get configured? | `triage.js` and `pr_runner.js`, `getBedrockClient()` (reads `AWS_REGION`; SDK reads other AWS env vars) |
| Volume cap enforcement | `pr_runner.js` `--max-prs`, `issue_runner.js` `--max-issues`. Both `slice()` the candidates array. |
| Branch naming for auto-PRs | `pr_runner.js`, `buildBranchName()` |
