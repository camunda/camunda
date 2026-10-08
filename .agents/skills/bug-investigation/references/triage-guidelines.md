# Triage Guidelines

This skill runs **after** an issue has already been through the repo's automated triage. It does
not re-triage — it reads the existing signals to shape how the investigation report is framed, and
scores its own confidence in the proposed fix.

## Reading the existing triage signals

Labels are applied automatically by `.github/opened_issue_labeler.yml` from the issue template's
selections, and org-level urgency is computed by `assign-urgency-to-issue.yml` from
`severity`/`likelihood`/`impact`/`when`. Read them, don't recompute them:

```bash
gh issue view <issue_number> --repo <owner>/<repo> --json labels,issueType
```

| Label prefix    | Meaning                                                              |
|-----------------|-----------------------------------------------------------------------|
| `severity/*`    | `critical` \| `high` \| `mid` \| `low` \| `unknown` — impact if it fires |
| `likelihood/*`  | `high` \| `mid` \| `low` \| `unknown` — how often it fires             |
| `component/*`   | owning module — see the path map in the repo's `AGENTS.md`            |
| `affects/8.x`   | release lines the bug affects                                        |
| `discovered-by/*` | how the bug surfaced (load tests, chaos day, gameday, manual, QA automation) |

Org-level **urgency** (`immediate` / `next` / `planned` / `someday`) is derived from severity ×
likelihood — `critical` severity always maps to `immediate`. This skill's target is `next` and
`immediate` bugs (per the problem this skill solves); if invoked on a `planned`/`someday` issue,
proceed anyway but note in the report that urgency is lower than the skill's usual target — it
doesn't change the investigation itself.

**Do not** re-label, re-classify severity, or re-route ownership. If the existing labels look wrong
for what the investigation actually found (e.g. severity/low but reproduction shows data loss),
say so explicitly in the report as a finding — don't silently relabel the issue.

## Bug type classification (drives Phase 3's reproduction method)

Read the issue body and comments for these signals, in this priority order (a UI screenshot with an
API stack trace underneath is still a UI bug for reproduction purposes — reproduce what the user
saw first):

1. **UI** — screenshot, video, a specific Operate/Tasklist/Identity page/component named, or the
   issue was filed via the app's "report a bug" flow.
2. **Flaky test** — "flaky", "sometimes fails", "intermittent", a link to a failed CI run for an
   existing test, `kind/flake` label already present.
3. **API/behavior** — curl/REST/gRPC repro steps, request/response bodies pasted, no UI involved.
4. **Backend/engine** — stack trace only, log lines, no reproducible request/response or UI path
   given.

## Confidence scoring

Confidence is not a single signal — it's built from three inputs, each independently capped, then
combined. A fix cannot score higher than its weakest input.

Before scoring any of the three inputs, confirm the code you investigated actually matches the
issue: if the issue supplied a verbatim identifier (a fully-qualified class name, log message, or
stack trace frame — see `SKILL.md` Hard rule 10) and you never found it, don't score Root cause
clarity from whatever similar-looking code you did find. A plausible-sounding root cause in the
wrong file isn't a weak signal on one input — it invalidates Reproduction and Root cause clarity
both, since neither actually examined the reported code path.

| Input                          | High (contributes ≥ 0.80)                          | Medium (contributes 0.50–0.79)         | Low (contributes < 0.50)                |
|---------------------------------|-------------------------------------------------------|-------------------------------------------|---------------------------------------------|
| Reproduction (Phase 3)          | Confirmed — bug actually observed                    | Could not reproduce, but root cause is still clear from code + issue | Environment failed to start, or evidence is contradictory |
| Root cause clarity (Phase 4)    | Specific function/line, explains **all** reported symptoms | Explains most symptoms, one open question remains | Root cause is a guess among plausible alternatives |
| Fix validation (Phase 5)        | All 6 checks pass                                     | 5 of 6 pass, the failing one is minor (e.g. pattern check inconclusive because no prior similar fix exists) | Any of static/semantic checks fail, simulation doesn't clearly resolve the repro, or the backend-parity check fails (fix doesn't cover every backend the bug reproduces on) |

Combine by taking the **minimum** across the three inputs, then apply the diff-size gate from
`SKILL.md`'s Hard rule 6 (≥ 1000 estimated lines caps the *decision*, not the score, at Medium
treatment).

Always name the weakest input in the report — "Medium confidence: root cause is clear and the fix
passes all six validation checks, but reproduction was not possible without production logs" is a
useful report; "confidence: Medium" alone is not.

## What "logs would help" means in practice

When something in the issue (severity, an error message, a stack trace) suggests logs exist but
weren't attached, note it as a specific, actionable ask in the report — not a generic "please
provide more info":

> Open question: the reported `NullPointerException` at `<permalink>` is only reachable if
> `<condition>` is true. Broker logs around the failure timestamp would confirm whether that
> condition held — this would move confidence from Medium to High.

This is strictly informational. Per `SKILL.md` Hard rule 4, never pause the investigation or wait
for a response before finishing the report.
